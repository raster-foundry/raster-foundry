package com.azavea.rf.database.tables

import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.database.fields.{UserToOrganizationFields, OrganizationFkFields, TimestampFields}
import com.azavea.rf.database.sort._
import com.azavea.rf.datamodel._

class UsersToOrganizations(_tableTag: Tag) extends Table[User.ToOrganization](_tableTag, "users_to_organizations")
                                                   with UserToOrganizationFields
                                                   with TimestampFields
                                                   with OrganizationFkFields
{
  def * = (userId, organizationId, role, createdAt, modifiedAt) <> (User.ToOrganization.tupled, User.ToOrganization.unapply)
  /** Maps whole row to an option. Useful for outer joins. */
  def ? = (Rep.Some(userId), Rep.Some(organizationId), Rep.Some(role), Rep.Some(createdAt), Rep.Some(modifiedAt)).shaped.<>(
    {r=>import r._; _1.map(_=> User.ToOrganization.tupled((_1.get, _2.get, _3.get, _4.get, _5.get)))},
    (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

  val userId: Rep[String] = column[String]("user_id", O.Length(255,varying=true))
  val organizationId: Rep[java.util.UUID] = column[java.util.UUID]("organization_id")
  val role: Rep[User.Role] = column[User.Role]("role", O.Length(255,varying=true))
  val createdAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created_at")
  val modifiedAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("modified_at")

  val pk = primaryKey("users_to_organizations_pkey", (userId, organizationId))

  lazy val organizationsFk = foreignKey("users_to_organizations_organization_id_fkey", organizationId, Organizations)(r =>
    r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val usersFk = foreignKey("users_to_organizations_user_id_fkey", userId, Users)(r =>
    r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
}

object UsersToOrganizations extends TableQuery(tag => new UsersToOrganizations(tag)) {
  implicit val sorter =
    new QuerySorter[UsersToOrganizations](
      new UserToOrganizationSort(identity[UsersToOrganizations]),
      new TimestampSort(identity[UsersToOrganizations]),
      new OrganizationFkSort(identity[UsersToOrganizations]))
}
