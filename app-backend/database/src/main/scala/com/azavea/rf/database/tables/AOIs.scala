package com.azavea.rf.database.tables

import java.sql.Timestamp
import java.util.{Date, UUID}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import cats.data.OptionT
import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.database.fields.{OrganizationFkFields, TimestampFields, UserFkFields}
import com.azavea.rf.database.sort._
import com.azavea.rf.datamodel.{AOI, User}
import com.typesafe.scalalogging.LazyLogging
import geotrellis.slick.Projected
import geotrellis.vector.MultiPolygon

// --- //

/** The table description for the "aois" table. */
class AOIs(_tableTag: Tag) extends Table[AOI](_tableTag, "aois")
    with TimestampFields with OrganizationFkFields with UserFkFields {

  def * = (id, createdAt, modifiedAt, organizationId, createdBy, modifiedBy, area) <> (AOI.tupled, AOI.unapply)

  /* Database Fields */
  val id: Rep[UUID] = column[UUID]("id", O.PrimaryKey)
  val createdAt: Rep[Timestamp] = column[Timestamp]("created_at")
  val modifiedAt: Rep[Timestamp] = column[Timestamp]("modified_at")
  val organizationId: Rep[UUID] = column[UUID]("organization_id")
  val createdBy: Rep[String] = column[String]("created_by", O.Length(255,varying=true))
  val modifiedBy: Rep[String] = column[String]("modified_by", O.Length(255,varying=true))

  /* Unique Fields */
  val area: Rep[Projected[MultiPolygon]] = column[Projected[MultiPolygon]]("area")

  /* Foreign Keys */
  lazy val organizationsFk = foreignKey("aois_organization_id_fkey", organizationId, Organizations)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val createdByUserFK = foreignKey("aois_created_by_fkey", createdBy, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val modifiedByUserFK = foreignKey("aois_modified_by_fkey", modifiedBy, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)

}

object AOIs extends TableQuery(tag => new AOIs(tag)) with LazyLogging {
  type TableQuery = Query[AOIs, AOI, Seq]

  implicit val aoiSorter: QuerySorter[AOIs] =
    new QuerySorter(
      new OrganizationFkSort(identity),
      new TimestampSort(identity))

  /** Add an AOI to the database. */
  def insertAOI(aoi: AOI)(implicit db: DB): Future[AOI] =
    db.db.run(AOIs.forceInsert(aoi)).map(_ => aoi)

  /** Get an [[AOI]] given its UUID. */
  def getAOI(aoi: UUID)(implicit db: DB): OptionT[Future, AOI] =
    OptionT(db.db.run(AOIs.filter(_.id === aoi).result.headOption))

  /** Delete an [[AOI]] given its UUID. */
  def deleteAOI(aoi: UUID)(implicit db: DB): Future[Int] =
    db.db.run(AOIs.filter(_.id === aoi).delete)

  def updateAOI(aoi: AOI, aoiId: UUID, user: User)(implicit db: DB): Future[Int] = {

    val now = new Timestamp((new Date()).getTime)

    val query = AOIs.filter(_.id === aoiId)
      .map(a => (a.modifiedAt, a.modifiedBy, a.area))

    db.db.run(query.update((now, user.id, aoi.area))).map {
      case 1 => 1
      case c => throw new IllegalStateException(s"Error updating project: update result expected to be 1, was $c")
    }
  }

}
