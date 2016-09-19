package com.azavea.rf.datamodel.v1.schema
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
  lazy val schema: profile.SchemaDescription = Organizations.schema
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
}
object Version{
  def version = 1
}
