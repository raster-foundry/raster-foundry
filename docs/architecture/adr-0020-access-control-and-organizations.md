# Permissions + Architecture for Organizations

## Context

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

On the other hand implementing an access-control system using ABAC would allow the access-control system to more closely parallel what is presented to users.

I propose that ABAC is the approach that we take.

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
* **type** -  `PLATFORM`, `ORGANIZATION`, `TEAM`
* **type_id** - uuid of specific group
* **role** - `ADMIN`, `MEMBER` (using enums rather than an `is_admin` fields leaves room for more roles)

This could also be done with separate tables for each `type` rather than combining all into one table.

##### Questions + Comments

* Currently, we think that a user should only belong to one organization and that if a user would need to move to a different organization, they would need to create another account. This may be a problematic approach as we currently authenticate via email which would then be the same for both accounts (which we can’t support).
* Can we think of a better name than `type` and `type_id` which are not descriptive at all?
* Does this eliminate the need for the `organization` and `role`  columns on users? I lean towards yes.
