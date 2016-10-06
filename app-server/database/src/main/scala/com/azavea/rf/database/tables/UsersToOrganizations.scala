package com.azavea.rf.database.tables

import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.datamodel._
import java.sql.Timestamp
import java.util.UUID
import com.typesafe.scalalogging.LazyLogging

class UsersToOrganizations(_tableTag: Tag) extends Table[UserToOrganization](_tableTag, "users_to_organizations") {
  def * = (userId, organizationId, role, createdAt, modifiedAt) <> (UserToOrganization.tupled, UserToOrganization.unapply)
  /** Maps whole row to an option. Useful for outer joins. */
  def ? = (Rep.Some(userId), Rep.Some(organizationId), Rep.Some(role), Rep.Some(createdAt), Rep.Some(modifiedAt)).shaped.<>(
    {r=>import r._; _1.map(_=> UserToOrganization.tupled((_1.get, _2.get, _3.get, _4.get, _5.get)))},
    (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

  val userId: Rep[String] = column[String]("user_id", O.Length(255,varying=true))
  val organizationId: Rep[java.util.UUID] = column[java.util.UUID]("organization_id")
  val role: Rep[String] = column[String]("role", O.Length(255,varying=true))
  val createdAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created_at")
  val modifiedAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("modified_at")

  /** Primary key of UsersToOrganizations (database name users_to_organizations_pkey) */
  val pk = primaryKey("users_to_organizations_pkey", (userId, organizationId))

  /** Foreign key referencing Organizations (database name users_to_organizations_organization_id_fkey) */
  lazy val organizationsFk = foreignKey("users_to_organizations_organization_id_fkey", organizationId, Organizations)(r =>
    r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  /** Foreign key referencing Users (database name users_to_organizations_user_id_fkey) */
  lazy val usersFk = foreignKey("users_to_organizations_user_id_fkey", userId, Users)(r =>
    r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
}

object UsersToOrganizations extends TableQuery(tag => new UsersToOrganizations(tag)) {

}
