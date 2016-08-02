package com.azavea.rf.datamodel.v4.schema
// AUTO-GENERATED Slick data model
/** Stand-alone Slick data model for immediate use */
object tables extends {
  val profile = slick.driver.PostgresDriver
} with tables

/** Slick data model trait for extension, choice of backend or usage in the cake pattern. (Make sure to initialize this late.) */
trait tables {
  val profile: slick.driver.JdbcProfile
  import profile.api._
  import slick.model.ForeignKeyAction
  // NOTE: GetResult mappers for plain SQL are only generated for tables where Slick knows how to map the types of all columns.
  import slick.jdbc.{GetResult => GR}

  /** DDL for all tables. Call .create to execute. */
  lazy val schema: profile.SchemaDescription = Organizations.schema ++ Users.schema
  @deprecated("Use .schema instead of .ddl", "3.0")
  def ddl = schema

  /** Entity class storing rows of table Organizations
   *  @param id Database column id SqlType(uuid), PrimaryKey
   *  @param createdAt Database column created_at SqlType(timestamp)
   *  @param modifiedAt Database column modified_at SqlType(timestamp)
   *  @param name Database column name SqlType(varchar), Length(255,true) */
  case class OrganizationsRow(id: java.util.UUID, createdAt: java.sql.Timestamp, modifiedAt: java.sql.Timestamp, name: String)
  /** GetResult implicit for fetching OrganizationsRow objects using plain SQL queries */
  implicit def GetResultOrganizationsRow(implicit e0: GR[java.util.UUID], e1: GR[java.sql.Timestamp], e2: GR[String]): GR[OrganizationsRow] = GR{
    prs => import prs._
    OrganizationsRow.tupled((<<[java.util.UUID], <<[java.sql.Timestamp], <<[java.sql.Timestamp], <<[String]))
  }
  /** Table description of table organizations. Objects of this class serve as prototypes for rows in queries. */
  class Organizations(_tableTag: Tag) extends Table[OrganizationsRow](_tableTag, "organizations") {
    def * = (id, createdAt, modifiedAt, name) <> (OrganizationsRow.tupled, OrganizationsRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), Rep.Some(createdAt), Rep.Some(modifiedAt), Rep.Some(name)).shaped.<>({r=>import r._; _1.map(_=> OrganizationsRow.tupled((_1.get, _2.get, _3.get, _4.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(uuid), PrimaryKey */
    val id: Rep[java.util.UUID] = column[java.util.UUID]("id", O.PrimaryKey)
    /** Database column created_at SqlType(timestamp) */
    val createdAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created_at")
    /** Database column modified_at SqlType(timestamp) */
    val modifiedAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("modified_at")
    /** Database column name SqlType(varchar), Length(255,true) */
    val name: Rep[String] = column[String]("name", O.Length(255,varying=true))
  }
  /** Collection-like TableQuery object for table Organizations */
  lazy val Organizations = new TableQuery(tag => new Organizations(tag))

  /** Entity class storing rows of table Users
   *  @param id Database column id SqlType(uuid), PrimaryKey
   *  @param createdAt Database column created_at SqlType(timestamp)
   *  @param modifiedAt Database column modified_at SqlType(timestamp)
   *  @param isActive Database column is_active SqlType(bool), Default(Some(true))
   *  @param isStaff Database column is_staff SqlType(bool), Default(Some(false))
   *  @param email Database column email SqlType(varchar), Length(255,true)
   *  @param firstName Database column first_name SqlType(varchar), Length(255,true)
   *  @param lastName Database column last_name SqlType(varchar), Length(255,true)
   *  @param organizationId Database column organization_id SqlType(uuid) */
  case class UsersRow(id: java.util.UUID, createdAt: java.sql.Timestamp, modifiedAt: java.sql.Timestamp, isActive: Option[Boolean] = Some(true), isStaff: Option[Boolean] = Some(false), email: String, firstName: String, lastName: String, organizationId: java.util.UUID)
  /** GetResult implicit for fetching UsersRow objects using plain SQL queries */
  implicit def GetResultUsersRow(implicit e0: GR[java.util.UUID], e1: GR[java.sql.Timestamp], e2: GR[Option[Boolean]], e3: GR[String]): GR[UsersRow] = GR{
    prs => import prs._
    UsersRow.tupled((<<[java.util.UUID], <<[java.sql.Timestamp], <<[java.sql.Timestamp], <<?[Boolean], <<?[Boolean], <<[String], <<[String], <<[String], <<[java.util.UUID]))
  }
  /** Table description of table users. Objects of this class serve as prototypes for rows in queries. */
  class Users(_tableTag: Tag) extends Table[UsersRow](_tableTag, "users") {
    def * = (id, createdAt, modifiedAt, isActive, isStaff, email, firstName, lastName, organizationId) <> (UsersRow.tupled, UsersRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), Rep.Some(createdAt), Rep.Some(modifiedAt), isActive, isStaff, Rep.Some(email), Rep.Some(firstName), Rep.Some(lastName), Rep.Some(organizationId)).shaped.<>({r=>import r._; _1.map(_=> UsersRow.tupled((_1.get, _2.get, _3.get, _4, _5, _6.get, _7.get, _8.get, _9.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(uuid), PrimaryKey */
    val id: Rep[java.util.UUID] = column[java.util.UUID]("id", O.PrimaryKey)
    /** Database column created_at SqlType(timestamp) */
    val createdAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created_at")
    /** Database column modified_at SqlType(timestamp) */
    val modifiedAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("modified_at")
    /** Database column is_active SqlType(bool), Default(Some(true)) */
    val isActive: Rep[Option[Boolean]] = column[Option[Boolean]]("is_active", O.Default(Some(true)))
    /** Database column is_staff SqlType(bool), Default(Some(false)) */
    val isStaff: Rep[Option[Boolean]] = column[Option[Boolean]]("is_staff", O.Default(Some(false)))
    /** Database column email SqlType(varchar), Length(255,true) */
    val email: Rep[String] = column[String]("email", O.Length(255,varying=true))
    /** Database column first_name SqlType(varchar), Length(255,true) */
    val firstName: Rep[String] = column[String]("first_name", O.Length(255,varying=true))
    /** Database column last_name SqlType(varchar), Length(255,true) */
    val lastName: Rep[String] = column[String]("last_name", O.Length(255,varying=true))
    /** Database column organization_id SqlType(uuid) */
    val organizationId: Rep[java.util.UUID] = column[java.util.UUID]("organization_id")

    /** Foreign key referencing Organizations (database name users_organization_id_fkey) */
    lazy val organizationsFk = foreignKey("users_organization_id_fkey", organizationId, Organizations)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)

    /** Uniqueness Index over (email) (database name users_email_key) */
    val index1 = index("users_email_key", email, unique=true)
  }
  /** Collection-like TableQuery object for table Users */
  lazy val Users = new TableQuery(tag => new Users(tag))
}
object Version{
  def version = 4
}
