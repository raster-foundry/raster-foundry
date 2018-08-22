# 0023 - Object ACRs in text array

## Context

This ADR supersedes `ADR-0020` concerning the implementation of Access Control Rules(ACRs).

In Raster Foundry, we implemented a mechanism called "Access Control Rules" (ACRs) to determine, manage, and update a user's access to some first-class objects -- projects, scenes, datasources, shapes, workspaces, templates, and analyses. Such ACRs are stored in a table so that the access to an object is determined by a user's ownership, membership of a team or an organization or a platform, directly granted access, etc.

The ACRs worked fine at the beginning of this mechanism. But as the quantity of these objects grows, especially the number of scenes, the ACRs table has quickly expanded to a giant one. This has resulted in slow to timed-out queries, especially for scenes, due to `JOIN`s and `UNION`s on a giant ACR table, object table, and "User Group Roles" (UGRs) table.

We have taken some actions to remedy it -- optimizing SQL queries, increasing instance sizes, deleting unnecessary ACR records, etc. But looking forward, we need to find a more sustainable way, especially because we have to speed up those fundamental queries like scene searches. The next section of this ADR lands on a decision that still uses ACRs but combines them more closely with objects to avoid `JOIN`s and `UNION`s among increasingly huge tables as much as possible. Then it suggests consequential changes to adapt to the updated mechanism in the last section, along with some underlying risks involved.


## Decision

We will add `text[]` fields to all first-class objects, migrate ACRs from the `access_control_rules` table to object level ACRs, create `GIN` indices on these new fields, drop the original ACR table, and adapt all ACR-related endpoints, data models, SQL commands, property tests to use this object level text array field. Querying on scenes filtered by this text array filed of ACRs has been proven to be a lot faster than before within the copy of the production database (more proofs in experiments [here](https://github.com/azavea/raster-foundry-platform/issues/431#issuecomment-409882125)).


## Consequences

### Format of ACR strings

The ACRs formerly consist of three types of information: object (type and id of projects, scenes, etc.), subject (type and id of a user, a team, an organization, etc.), and action (view, edit, delete, etc). Now that ACRs are on objects, we will drop object information and format ACR strings as `"<subject type>;<optional subject id>;<action type>` in the text array. Note that for objects granted `"ALL"` subject type, there will be no need for subject id (e.g. `"ALL;;VIEW"`), which indicates an object being granted a certain action type across the entire platform.

### Serialization and Deserialization

Doobie supports mappings between single-dimensional `text[]` and `List[String]`. To construct `"<subject type>;<optional subject id>;<action type>"` strings, we will create a data model to transform a list of access control rules to strings conforming to this structure before inserting to database, which can be easily tested using property based tests.

Text array ACRs should not be returned on any listing endpoints for first class objects. Only object owners are able to access ACRs through `api/<fist class object>/<object id>/permissions` endpoints, which we have been using, and which will need some adaptions.

### ACR-related actions

For `CRUD` of ACRs on an object, no special handling, other than importing `doobie.postgres._` and `doobie.postgres.implicits._`, will be required since doobie does the mappings. Note that, currently, when deleting a single permission, we update permissions with the rest of other permissions. So this is not a "delete" so to speak but more of a re-assigning. When deleting permission of an object, we will just pass an empty `text[]` to the `acrs` field.

In terms of querying for objects with filters on object level ACRs, postgres array functions such as `array_cat()` and `concat_ws()`, and operators such as `&&` (which determines overlay of two arrays), etc., could potentially be handy.

When listing actions or permissions by subject type and id, which will potentially require going into each string of the text array, we could use postgres array functions such as `unnest()` combined with `like` to match string patterns.

### Migration

Migrations could be a bit tricky and probably could take some time to run. Adding a new field and updating this field in all first-class objects' tables would likely require a novel approach of creating a temp table with the new column, writing to a new permanent table, and dropping the previous table. Further research on it will be in a future issue card.

Note that the `acrs` field will have an empty text array as the default value.

### Examples for datasources

This section shows some initial attempts for adapting to the ACR text array solution. These are for initial tests only and don't represent final implementation details. Datasources could be a simple case, but such adaptations can be applied to other first-class objects in the similar way.

#### Data models

```scala
@JsonCodec
case class Datasource(
  id: UUID,
  createdAt: java.sql.Timestamp,
  createdBy: String,
  modifiedAt: java.sql.Timestamp,
  modifiedBy: String,
  owner: String,
  name: String,
  visibility: Visibility,
  composites: Json,
  extras: Json,
  bands: Json,
  licenseName: Option[String],
  acrs: List[String]
)

@JsonCodec
case class ObjectAccessControlRuleCreate (
  subjectType: SubjectType,
  subjectIdO: Option[String],
  actionType: ActionType
) {
  val subjectId: String = subjectIdO match {
    case Some(subjectId) => subjectId
    case _ => ""
  }

  def toAccessControlRuleString: String =
    s"${subjectType.toString};${subjectId};${actionType.toString}"
}
```

#### CRUD

```scala
def getAcrsByObjectId(object: Datasource): Fragment =
  fr"SELECT acrs FROM datasources where id = ${datasource.id};"

def updatePermissions(objectAcrCreateList: List[ObjectAccessControlRuleCreate], datasourceId: UUID, user: User): ConnectionIO[Int] = {
    val now = new Timestamp((new java.util.Date()).getTime())
    val objectAcrs = objectAcrCreateList.map(_.toAccessControlRuleString)
    val updateQuery =
      fr"UPDATE" ++ this.tableF ++ fr"SET" ++
      fr"""
        modified_at = ${now},
        modified_by = ${user.id},
        acrs = ${objectAcrs}
      where id = ${datasourceId}
      """
    updateQuery.update.run
}
```

#### First-class objects query filtered by ACRs

```scala
def exampleListQuery(user: User, actionType: ActionType, tableF: Fragment): Fragment = {
  val baseF: Fragment = fr"SELECT * FROM" ++ tableF ++ "WHERE"
  val ownedF: Fragment = fr"owner = ${user.id}"
  val publicF: Fragment = fr"visibility = 'PUBLIC'"
  val sharedPlatF: Fragment = fr"ARRAY[${"ALL;;" + actionType.toString}]"
  val sharedUserF: Fragment = fr"ARRAY[${"USER;" + user.id + ";" + actionType.toString}]"
  val inheritedF: Fragment = fr"""
    ARRAY(
      SELECT concat_ws(';', group_type, group_id, ${actionType.toString})
      FROM user_group_roles
      WHERE user_id = '${user.id}'
    )
  """
  baseF ++ fr"""(
    -- owned by the requesting user only
    ${ownedF})

    -- for scenes only
    OR
    ${publicF}

    -- determine permission text array overlap with && operator
    OR
    array_cat(

      -- shared to the platform or to the user directly
      ${sharedPlatF},
      ${sharedUserF},

      -- inherited due to membership
      ${inheritedF}
    ) && acrs
  )

  -- other filters go here
  """
}
```

A note for `inheritedF`: it could be better practice to get a set of user group roles up front with a separate query. It would be easier to extend some of the practices here if we assumed the function `exampleListQuery` either took a function `(user => UserGroupRoles)` or a list of `UserGroupRoles` which would allow us to rely on caching or parsing user group roles from the JWT. It also seems less magical in a way to avoid the SQL around array formation.

#### List user actions

```scala
def listActionsByUser(user: User, datasourceId: UUID, tableF: Fragment): Fragment = fr"""
  SELECT a.* from
  (
    SELECT UNNEST(acrs) acrs
    from ${tableF}
    where id = ${datasource.id}) a
  WHERE
    a.acrs LIKE ${s"%${user.id}%"}
    OR a.acrs LIKE '%ALL%'
    OR a.acrs LIKE concat(
      '%',
      (SELECT text(group_id)
      FROM user_group_roles
      WHERE user_id = ${user.id}),
      '%')
    );
"""
```
