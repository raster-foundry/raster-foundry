# Permissions + Architecture for Organizations

## Context

This ADR has been superseded by `ADR-0023` concerning the implementation of Access Control Rules(ACRs).

We have a very basic implementation of organizations. In essence there are only two organizations - the root organization, and the default public organization. This was not how the feature was initially envisioned; a change of priorities and lack of data pushed it’s full implementation down the road to a point where we would have a better understanding of usage patterns and requirements. We now find ourselves at that point.

### Target Feature Set

#### Concepts

The target feature set introduces and redefines some administrative concepts:

* **Platforms** - Platforms are the top level unit of administration. A platform will be used to provide administrative control over a number of organizations. Platforms have an owner or group of owners who can administer the underlying organizations from the application. Platforms will be largely invisible to users not given administrative controls of a platform. Platforms are being implemented to allow large-scale organizations to use Raster Foundry as their foundational architecture. In these cases, whether the public infrastructure of Raster Foundry is being used or if Raster Foundry is being deployed on a private cloud, the platform level controls will make managing a large number of organizations more realistic.

* **Organizations** -  Organizations represent groups of users within platforms. Organizations will have two types of settings. One group of these settings are administered by the platform owners such as feature-flags and upload limits. Another set will be adjustable by the organization owners, such as logos and API keys.
  * Each organization belongs to **one** platform
  * A user can be a member of **at most one** organization
* **Teams** - Teams represent groups of users within (and across) organizations.
  * Each team belongs to **one** organization
  * A user can be a member of **any number** of teams

#### Mechanics

We need to be able determine a user’s access to any entity by taking into account multiple factors including:

* entity ownership
* team membership
* organization membership and role within the organization
* role within the platform
* explicitly granted access

##### Scenarios to Consider

The following scenarios will be used as a mechanism to test each access-control method against:

1. A member of an **organization** wants to allow any users in the **same organization** to **view** this project, while not allowing anyone from outside that organization to have any access.

2. An **admin** of an **organization** needs to be able to change their organization’s settings while a regular user within the organization or admins of other organizations should not be allowed to view or adjust these settings.

3. An **admin** of a **platform** needs to be able to change platform level settings while users who are not platform admins cannot view or change these settings.

4. A member of a **Team A** wants to allow any other user in **Team A** to be able to **view** and **edit** an analysis while not allowing anyone from outside the team to have any access.

5. A user **in Organization A** wants another user **in Organization B** to be able to **view** a project while ensuring that other users from **Organization B** have no access.

6. A user **not in Team A** wants all users in **Team A** to be able to **view** an analysis.

### Current Landscape + Existing Controls

While the current implementation is far from complete there are existing pieces of the API could be kept in place. This decision requires a full understanding of our current implementation.

#### User-Organization Relationship

Each user has an `organization` field. This allows for a 1 -> n relationship between organizations and users.
This is currently used for permissions checks with methods such as `isInRootOrganization` and `isInRootOrSameOrganizationAs`.

#### User Roles

Each user has a `role` field. This field is limited to three values:

* user
* viewer
* admin

This field is actually not used in any way.

#### Entity Ownership + Visibility

Each entity in our API has an owner (apart from organizations and users). This owner is used as the basis for permission checks in many cases with methods like `isInRootOrOwner`

Each entity in our API also has a visibility field. This field determines the visibility of the particular entity relative to the owning user’s organization. It has three possible values:

* **public** -  the entity should be visible to all users of all organizations
* **organization** - the entity should be visible to all users within the same organization as the owner
* **private** - the entity should be visible only to the owner of the entity

It is also assumed that an admin user can view entities regardless of the visibility setting.

### Access Control Methods

When attempting to understand the various methods and how they would be applied, it’s important to note that in some cases these methods are used in conjunction with each other while in other cases, one method is used to implement another method. Additionally, as with many standards, these methods are mostly described in theoretical terms while implementation details are seen as out of scope.

Some terminology that will be helpful:

* **object** or **resource** - the entity that should be protected from unauthorized access or actions (think CRUD). Some Raster Foundry objects are projects and analyses.
* **subject** - the entity attempting to access or perform an action on an object. In most cases this is a user, but could also be a machine-user
* **attributes** - properties of an entity or environmental conditions. Both objects and subjects have attributes such as owner, name, or timestamps. An environmental attribute could be time-of-day, day-of-week, or even the current location of the subject.

#### Mandatory Access Control (MAC)

MAC is an access control method where policies are applied to subjects and objects based on the security attributes of each to determine if access is allowed. Resource owners have no control over these policies and attributes.

> With mandatory access control, this security policy is centrally controlled by a security policy administrator; users do not have the ability to override the policy and, for example, grant access to files that would otherwise be restricted.
> [Mandatory access control - Wikipedia](https://en.wikipedia.org/wiki/Mandatory_access_control)

Many MAC implementations focused on access-control that was compatible with US government classified information systems in which the strict controls that MAC provides are necessary. In these cases the security attributes of a subject could be the subject’s clearance level, while the objects security attribute would be it’s classification level. A policy would then indicate that only subjects with ‘top-secret’ clearance can read objects with a ‘top-secret’ classification level.
A few contrived examples of what MAC might look like in Raster Foundry:

* a non-admin user can create an analysis but can never delete the analysis
* an admin user can create an analysis but no non-admin users can view this analysis

#### Discretionary Access Control (DAC)

DAC is similar to MAC in that policies are applied to subjects and object to determine if their attributes satisfy access requirements. DAC differs from MAC in that object owners can elect to modify the attributes of their objects and thereby grant access to other users (hence the ‘discretionary’ portion of the name).
An example of this would be UNIX file mode and the ability for a user to use `chmod` to adjust the attributes of a file they own.

#### Role-Based Access Control (RBAC)

RBAC uses roles that are assigned to users to determine access. RBAC can also be used to enforce MAC and DAC. User’s are never assigned permissions directly. Instead, all permissions granted to a user are determined by their assigned roles.

With RBAC, roles are intended to define a user’s permissions in terms of specific operations that have organizational meaning — for Raster Foundry, such an operation might be ‘adding bands to a datasource’ — rather than defining a user’s permissions in terms of objects themselves, such as ‘editing a datasource’.

A typical RBAC data model is roles are granted permissions and a permission maps an operation to an object. This means that as the pool of objects expand, the permissions granted to roles also expand. For example, when a project with id 1234 is created, a permission `project1234.get` would be created and added to the `admin` role.  Adding the permissions to the `admin` role avoids having to directly grant permissions to a large number of users who have that role.

#### Access Control Lists (ACL) or Identity-Based Access Control (IBAC)

ACLs are a list of identities allowed to access a specific resource. As such, each resource has its own ACL. Each attempted access requires ensuring that the subject’s identity is included in the object’s ACL. ACL implementations differ in complexity. In some cases, each entry of an ACL also contains a specific operation. For example, an entry may indicate that User A may be allowed to read the object while another entry indicates that User A can write to the object. In this sense, the user’s access is accumulated over the ACL.

#### Attribute-Based Access Control (ABAC)

ABAC is considered the successor to RBAC. It can be seen as policy based (similar to MAC), but is far more flexible because of the various attribute sources that can be used to compute access rights. Additionally, unlike MAC, ABAC does not prohibit resource owners having control of permissions . For example, the time-of-day could be an attribute used in creating an access-policy: “A user can only create a deposit between 9AM and 5PM”.

ABACs flexibility is such that it can be used to implement  MAC, DAC, RBAC and ACLs. In the case of RBAC, the evaluated subject attribute would be _role_ while for an ACL the subject attribute might be _id_ while the object attribute would be the ACL itself.

ABAC policies are often expressed in XACML [XACML - Wikipedia](https://en.wikipedia.org/wiki/XACML), but that is not a requirement of ABAC.

## Access-Control Analysis

With the contextual knowledge, we can now go back and look at the scenarios that were previously listed and see how they best might be implemented.

### Scenario 1

> A member of an **organization** wants to allow any users in the **same organization** to **view** this project, while not allowing anyone from outside that organization to have any access.


This would be best implemented with DAC, or ABAC . The subject in this case is someone trying to access this project, not the person creating the project.

**Subject attributes**: organization

**Object attributes**: visibility -> `ORGANIZATION`, organization

**Policy**: members of organizations have **read** access to any projects with the same organization

MAC is not suitable as the user needs to have control over the visibility of the object. We’ve already implemented this and use the same attributes to determine access.

### Scenario 2

> An **admin** of an **organization** needs to be able to change their organization’s settings while a regular user within the organization or admins of other organizations should not be allowed to view or adjust these settings.


This could be implemented with any of the methods, but RBAC makes the most sense would assume that a role specific to each organization exists for Admins. Any subject would need to have this role to view or change these settings.

### Scenario 3

> An **admin** of a **platform** needs to be able to change platform level settings while users who are not platform admins cannot view or change these settings.


RBAC makes sense for this scenario as well, assuming that a role for each platform exists.

### Scenario 4

> A member of a **Team A** wants to allow any other user in **Team A** to be able to **view** and **edit** an analysis while not allowing anyone from outside the team to have any access.


RBAC is an option here. It would involve having a role for being a member each team. The permissions to view and edit this specific analysis would then be added to the team-member role for this specific team. Things certainly begin to get more convoluted at this level.

ABAC may be a better option at this point (note that this does involve implementing an ACL within ABAC), note that the subject is still the person attempting access:

**Subject attributes**: team-membership

**Object attributes**: visibility - `PRIVATE`, ACL - `[('team', <team_id>,'view'), ('team', <team_id>, 'edit')]`

**Policy**:

* only creator of object can access a `PRIVATE` entity
* ACL - ‘team’ entry would check against subject’s team-membership and grant specific permissions if they match

### Scenario 5

> A user **in Organization A** wants another user **in Organization B** to be able to **view** a project while ensuring that other users from **Organization B** have no access.


RBAC would be very messy here as it would involve a new role specifically for this project, adding the needed permissions to the role, and then granting the other user that role.
Instead, this seems like a clear case for an ACL (via ABAC). This would mean adding the single user to the project’s ACL with ‘view’ permissions.

**Subject attributes**: user-id

**Object attributes**: visibility - `PRIVATE`, ACL - `[('user', <user_id>,'view')]`

**Policy**:

* only creator of object can access a `PRIVATE` entity
* ACL - ‘user’ entry would check against subject’ user-id and grant specific permissions if they match

### Scenario 6

> A user **not in Team A** wants all users in **Team A** to be able to **view** an analysis.


This scenario would be handled similarly to the way Scenario 4, but with a different ACL:

**Subject attributes**: team-membership

**Object attributes**: visibility - `PRIVATE`, ACL - `[('team', <team_id>,'view')]`

**Policy**:

* only creator of object can access a `PRIVATE` entity
* ACL - ‘team’ entry would check against subject’s team-membership and grant specific permissions if they match

## Proposed Access Control Approach

The scenarios above make’s a few things very clear:

1. We need an access-control system that has a concept of ACLs
2. We need an access-control system that has a concept of Roles

While you can do almost anything with RBAC, the risk of ‘role-explosion’ is very real and if RBAC is implemented so as to emulate ACL functionality, it is unavoidable. In addition, the administrative overhead of RBAC would be significant. It would most-likely be necessary to present an interface to users that largely obscures the underlying access-control system.

On the other hand implementing an access-control system using ACLs would allow the access-control system to more closely parallel what is presented to users.

I propose that an ACL-centric is the approach that we take.

### Implementation Details

#### Access-Control Computations

Within the process of completing some action on some object, there will need to be an access-control computation to determine if the subject has the necessary permissions to complete the action.

Using the Akka `authorize` directive ([authorize • Akka HTTP](https://doc.akka.io/docs/akka-http/current/routing-dsl/directives/security-directives/authorize.html)) with a per object policy set seems like it would be a good approach.  Each ‘policy’ should be a method that can leverage the request, the subject, and the object and return a boolean to indicate if access is allowed. It would also be beneficial to unit test each policy as well as object-level policy-sets as a whole.

##### Possible Issues

* Doing all access-control logic with API level code rather than at the DB level could incur some large reads.
* How do we define and apply policies to the subject and object?

#### ACL

From an ABAC perspective we should treat ACLs as object level attributes. From an architectural perspective, an ACL table would enable queries to better leverage the ACLs and improve performance for list queries. A possible schema for this table could be:

* **id** - UUID
* **object_type** - enum of object types: `PROJECT`, `ANALYSIS`, etc.
* **object_id** UUID pointing to a specific object
* **subject_type** - enum of possible control levels `PUBLIC`, `PLATFORM`, `ORGANIZATION`, `TEAM`, `USER`
* **subject_id** - UUID pointing to the specific subject (nullable as `PUBLIC` would have no id)
* **allowed_actions** - this field could be handled several ways. If it was limited to an enum of action types there could be multiple rows to fully represent an access rule (one row for read, one for write, etc). If we used a bit mask we could represent all possible permissions in a single column. The other option is a separate boolean column for each action.

While most ACLs operate at a subject level — they relate specific permissions to specific users. I propose that we implement a multi-level ACL that reflects our user-organization structure. This means allowing an ACL entry to reference any one of the following: platform, organization, team, or user.

ACLs should be the first access-control check done, as they would override any other policies.

ACLs would also effectively eliminate the need for the `visibility` field that is present on all resources as the ACL would allow not only defining object visibility with more fine-grained control than `PRIVATE`, `ORGANZATION` and `PUBLIC`.  The only issue with this is that we don’t currently have an approach to replicating the `PUBLIC` option, though allowing access at the `PLATFORM` level may be sufficient.

##### Questions

* Does storing an ACL on each entity make sense or should they be in a separate table?
* Does JSON make sense or is there a better type?
* Are all those subject levels necessary?
* Are there any actions missing?
* Can we eliminate the `visibility` field on all resources and instead use the ACL exclusively?
* ~~How can we replicate the `PUBLIC` visibility setting? Is a `PLATFORM` level ACL entry equivalent?~~
* Is there any need for negative permissions? For example, specifically denying access to a certain person or team?

#### Roles

We should handle roles as subject attributes. This would best be accomplished via a relationship table where each entry could be constructed like so:

* **id** - uuid
* **user** - uuid of user role applies to
* **group_type** -  `PLATFORM`, `ORGANIZATION`, `TEAM`
* **group_id** - uuid of specific group
* **role** - `ADMIN`, `MEMBER` (using enums rather than an `is_admin` fields leaves room for more roles)

This could also be done with separate tables for each `type` rather than combining all into one table.

##### Questions + Comments

* Currently, we think that a user should only belong to one organization and that if a user would need to move to a different organization, they would need to create another account. This may be a problematic approach as we currently authenticate via email which would then be the same for both accounts (which we can’t support).
* ~~Can we think of a better name than `type` and `type_id` which are not descriptive at all?~~
* Does this eliminate the need for the `organization` and `role`  columns on users? I lean towards yes.

## Sample Implementation

The sample implementation makes several changes:

* Adds tables for `platforms`, `teams`, `user_group_roles`, and `access_controls`
* Adds enums for `group_type`, `group_role`, `object_type`, `subject_type`, `access_control_action`
* Adds a `platform_id` column to `organizations`
* Removes `role` and `organization_id` from `users`
* Seeds the database with testable users, orgs, teams, and AC rules for existing projects

All projects in the tests are owned by User A, who is in Platform A, Organization A, and Team A.

Users are assigned some platform, organization, and team roles:

* User B is in Platform A, in Organization A, and is on Team A
* User C is in Platform A, in Organization A, and is on Team B
* User D is in Platform A, in Organization B
* User E is in platform B, in Organization C

For this test the following access control rules have been applied:

* Project A is set to viewable for Organization A
* Project B is set to viewable for Team A
* Project B is set to viewable for User D
* Project C is set to viewable for Platform A
* Project D is set to viewable for Platform B

We'll test it by looking at what projects show up for each user. Here's what is expected:

* User B should be able to view Project A, Project B, and Project C
* User C should be able to view Project A and Project C
* User D should be able to view Project B and Project C
* User E should be able to view Project D


The following SQL will allow the proposed solution to be tested

```sql
CREATE TABLE platforms (
    id UUID PRIMARY KEY NOT NULL,
    name text NOT NULL
);

ALTER TABLE organizations
ADD platform_id UUID REFERENCES platforms(id);

CREATE TABLE teams (
    id UUID PRIMARY KEY NOT NULL,
    organization_id UUID REFERENCES organizations(id) NOT NULL,
    name text NOT NULL
);

CREATE TYPE group_type AS ENUM ('PLATFORM', 'ORGANIZATION', 'TEAM');
CREATE TYPE group_role AS ENUM ('ADMIN', 'MEMBER');

CREATE TABLE user_group_roles (
    id UUID PRIMARY KEY NOT NULL,
    user_id VARCHAR(255) REFERENCES users(id) NOT NULL,
    group_type group_type NOT NULL,
    group_id UUID NOT NULL,
    group_role group_role NOT NULL
);

CREATE TYPE object_type AS ENUM ('PROJECT', 'SCENE');
CREATE TYPE subject_type AS ENUM ('ALL', 'PLATFORM', 'ORGANIZATION', 'TEAM', 'USER');
CREATE TYPE access_control_action AS ENUM ('VIEW', 'EDIT');

CREATE TABLE access_controls (
    id UUID PRIMARY KEY NOT NULL,
    object_type object_type NOT NULL,
    object_id UUID NOT NULL,
    subject_type subject_type NOT NULL,
    subject_id text,
    allowed_action access_control_action NOT NULL
);

ALTER TABLE users
    DROP COLUMN role,
    DROP COLUMN organization_id;

-- Create a platform (A)
INSERT INTO platforms VALUES (
    'e9a78d11-9aec-4e5b-b0ef-4390b9a4d6be',
    'Platform A'
);

-- Create Platform B
INSERT INTO platforms VALUES (
    '6d2da834-ed3a-415c-bddc-5367d35d187b',
    'Platform B'
);

-- Create Organizations A + B in Platform A
INSERT INTO organizations VALUES (
    '964c0b39-880c-4b0d-8dc7-2f376902bc8a',
    NOW(),
    NOW(),
    'Organization A',
    'e9a78d11-9aec-4e5b-b0ef-4390b9a4d6be'
);

INSERT INTO organizations VALUES (
    'f589b957-5063-400b-a831-00828e965f32',
    NOW(),
    NOW(),
    'Organization B',
    'e9a78d11-9aec-4e5b-b0ef-4390b9a4d6be'
);

-- Create Organization C in Platform B
INSERT INTO organizations VALUES (
    '8d9262bc-a71b-4601-988f-80e7b8d18c2c',
    NOW(),
    NOW(),
    'Organization C',
    '6d2da834-ed3a-415c-bddc-5367d35d187b'
);

-- Create Team A in Organization A
INSERT INTO teams VALUES (
    '05d22066-e3c3-4aa4-9f0f-8529ccee237f',
    '964c0b39-880c-4b0d-8dc7-2f376902bc8a',
    'Team A'
);

-- Create Team B in Organization A
INSERT INTO teams VALUES (
    '22570baf-4520-43db-a00d-708c71a5c619',
    '964c0b39-880c-4b0d-8dc7-2f376902bc8a',
    'Team B'
);

-- Create Team C in Organization B
INSERT INTO teams VALUES (
    'ee1bc085-e066-4b37-93df-cb3649f74e58',
    'f589b957-5063-400b-a831-00828e965f32',
    'Team C'
);

-- Inserting fixture users
INSERT INTO users (id, created_at, modified_at) VALUES (
    '5b42822c-3b78-4009-80cd-ac00d272e952',
    NOW(),
    NOW()
), (
    '71ecbce0-78fb-420a-bf8e-3cfa4f186150',
    NOW(),
    NOW()
), (
    '34107534-95ad-40d8-b02c-d067b1e23c88',
    NOW(),
    NOW()
), (
    '4f4cc230-413c-47e5-87ae-775d90e1f41c',
    NOW(),
    NOW()
);

-- Make User A admin of platform
INSERT INTO user_group_roles VALUES (
    '087ed9dc-e561-456c-80ce-229b930ee393',
    'auth0|59318a9d2fbbca3e16bcfc92',
    'PLATFORM',
    'e9a78d11-9aec-4e5b-b0ef-4390b9a4d6be',
    'ADMIN'
);

-- Make User A admin of organization
-- The organization is not directly connected to the platform in this prototype
INSERT INTO user_group_roles VALUES (
    '35b968b8-52a4-42fb-a28f-bf9dfd3e42af',
    'auth0|59318a9d2fbbca3e16bcfc92',
    'ORGANIZATION',
    '964c0b39-880c-4b0d-8dc7-2f376902bc8a',
    'ADMIN'
);

-- Make User A admin of Team A
INSERT INTO user_group_roles VALUES (
    '60e97de2-f306-4526-9904-679cdf660e86',
    'auth0|59318a9d2fbbca3e16bcfc92',
    'TEAM',
    '05d22066-e3c3-4aa4-9f0f-8529ccee237f',
    'ADMIN'
);

-- Make User B a member of Platform A
INSERT INTO user_group_roles VALUES (
    '6268cb4d-b0d8-4a40-a296-cf58908e9de3',
    '5b42822c-3b78-4009-80cd-ac00d272e952',
    'PLATFORM',
    'e9a78d11-9aec-4e5b-b0ef-4390b9a4d6be',
    'MEMBER'
);

-- Make User B a member of Organization A
INSERT INTO user_group_roles VALUES (
    'f46522e7-642b-40f5-a597-379af8c59659',
    '5b42822c-3b78-4009-80cd-ac00d272e952',
    'ORGANIZATION',
    '964c0b39-880c-4b0d-8dc7-2f376902bc8a',
    'MEMBER'
);

-- Make User B a member of Team A
INSERT INTO user_group_roles VALUES (
    '9648d6b8-6adf-42b7-b800-31bc6e2dcff3',
    '5b42822c-3b78-4009-80cd-ac00d272e952',
    'TEAM',
    '05d22066-e3c3-4aa4-9f0f-8529ccee237f',
    'MEMBER'
);

-- Make User C a member of Platform A
INSERT INTO user_group_roles VALUES (
    '7ff5f698-e857-4939-a84e-8ff00423d418',
    '71ecbce0-78fb-420a-bf8e-3cfa4f186150',
    'PLATFORM',
    'e9a78d11-9aec-4e5b-b0ef-4390b9a4d6be',
    'MEMBER'
);

-- Make User C a member of Organization A
INSERT INTO user_group_roles VALUES (
    '8e0cf268-53a7-4484-a012-25568f9f518c',
    '71ecbce0-78fb-420a-bf8e-3cfa4f186150',
    'ORGANIZATION',
    '964c0b39-880c-4b0d-8dc7-2f376902bc8a',
    'MEMBER'
);

-- Make User C a member of Team B
INSERT INTO user_group_roles VALUES (
    '8ed5a905-070c-4219-84da-738e6446be18',
    '71ecbce0-78fb-420a-bf8e-3cfa4f186150',
    'TEAM',
    '22570baf-4520-43db-a00d-708c71a5c619',
    'MEMBER'
);

-- Make User D a member of Platform A
INSERT INTO user_group_roles VALUES (
    'd5a919b4-f10c-46db-9d3e-ab0ef8f44ead',
    '34107534-95ad-40d8-b02c-d067b1e23c88',
    'PLATFORM',
    'e9a78d11-9aec-4e5b-b0ef-4390b9a4d6be',
    'MEMBER'
);

-- Make User D a member of Organization B
INSERT INTO user_group_roles VALUES (
    '486a4474-1bdb-4c71-a7df-b2b2c46a75d8',
    '34107534-95ad-40d8-b02c-d067b1e23c88',
    'ORGANIZATION',
    '8d9262bc-a71b-4601-988f-80e7b8d18c2c',
    'MEMBER'
);

-- Make User D a member of Platform A
INSERT INTO user_group_roles VALUES (
    '1ecf1a0b-b37c-4832-bd62-1e3f59d4ddb8',
    '4f4cc230-413c-47e5-87ae-775d90e1f41c',
    'PLATFORM',
    '6d2da834-ed3a-415c-bddc-5367d35d187b',
    'MEMBER'
);

-- Make User D a member of Organization A
INSERT INTO user_group_roles VALUES (
    '9309231b-289b-4c13-a3ee-7ea72e1e86e3',
    '4f4cc230-413c-47e5-87ae-775d90e1f41c',
    'ORGANIZATION',
    'f589b957-5063-400b-a831-00828e965f32',
    'MEMBER'
);


INSERT INTO access_controls VALUES
-- Allow Project C to be viewed by those in Platform A
(
    '3a644710-a2b6-4473-bffd-7c5997735697',
    'PROJECT',
    '2cc59c57-568d-4ced-99db-221eb6b4ca3d',
    'PLATFORM',
    'e9a78d11-9aec-4e5b-b0ef-4390b9a4d6be',
    'VIEW'
),
-- Allow Project A to be viewed by those in Organization A
(
    '9c2a8a20-9575-4439-bd6b-4a812c0258f7',
    'PROJECT',
    '91ba3348-f7ca-4b66-bacb-a119ca614742',
    'ORGANIZATION',
    '964c0b39-880c-4b0d-8dc7-2f376902bc8a',
    'VIEW'
),
-- Allow Project B to be viewed by those on Team A
(
    'f3d69c65-0ead-4916-ba7c-c42eb2bd38d4',
    'PROJECT',
    '30ee749c-7bf3-4d28-838a-d4aeeb451911',
    'TEAM',
    '05d22066-e3c3-4aa4-9f0f-8529ccee237f',
    'VIEW'
),
-- Allow Project D to be viewed by those in Platform B
(
    '3dfbf05b-b239-4eae-ab43-fd4e7f1c3259',
    'PROJECT',
    '3bc4ca13-d63e-4d62-ba22-363f28144ed2',
    'PLATFORM',
    '6d2da834-ed3a-415c-bddc-5367d35d187b',
    'VIEW'
),
-- Allow Project B to be viewed by User D
(
    'd68f926f-bd70-4c25-8fec-8de8ff388719',
    'PROJECT',
    '30ee749c-7bf3-4d28-838a-d4aeeb451911',
    'USER',
    '34107534-95ad-40d8-b02c-d067b1e23c88',
    'VIEW'
);

UPDATE projects SET name = 'Project A' WHERE id='91ba3348-f7ca-4b66-bacb-a119ca614742';
UPDATE projects SET name = 'Project B' WHERE id='30ee749c-7bf3-4d28-838a-d4aeeb451911';
UPDATE projects SET name = 'Project C' WHERE id='2cc59c57-568d-4ced-99db-221eb6b4ca3d';
UPDATE projects SET name = 'Project D' WHERE id='3bc4ca13-d63e-4d62-ba22-363f28144ed2';
```

The following SQL is a template for listing specific objects that a user has access to.
It takes three parameters, `$OBJECT_TYPE`, `$ACTION_TYPE`, and `$USER_ID`.
This sql doesn't currently handle ownership since we want to display the applied AC rule.

```sql
SELECT
  DISTINCT ON (p.id, acl.allowed_action)
  acl.subject_type, acl.subject_id, acl.allowed_action, p.name
FROM user_group_roles ugr
JOIN access_controls acl
    ON acl.object_type = $OBJECT_TYPE
    AND acl.allowed_action = $ACTION_TYPE
    AND
    ( acl.subject_type = 'ALL' OR (
            acl.subject_type::text = ugr.group_type::text
            AND acl.subject_id::text = ugr.group_id::text
        ) OR (
            acl.subject_type = 'USER'
            AND acl.subject_id = ugr.user_id
        )
    )
JOIN projects p
    ON p.id::text = acl.object_id::text
WHERE ugr.user_id = $USER_ID;
```

For these tests, `$OBJECT_TYPE` and `$ACTION TYPE` will always be `PROJECT` and `VIEW` respectively. Change `$USER_ID` to see what projects that user would be able to view (other than their own).
