package com.azavea.rf.database.tables

import java.sql.Timestamp
import java.util.{Date, UUID}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import cats.data.OptionT
import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.database.fields.{OrganizationFkFields, TimestampFields, UserFkFields}
import com.azavea.rf.database.query.AoiQueryParameters
import com.azavea.rf.database.sort._
import com.azavea.rf.datamodel.{AOI, AoiToProject, PaginatedResponse, User}
import com.lonelyplanet.akka.http.extensions.PageRequest
import com.typesafe.scalalogging.LazyLogging
import geotrellis.slick.Projected
import geotrellis.vector.MultiPolygon
import io.circe.Json

// --- //

/** The table description for the "aois" table. */
class AOIs(_tableTag: Tag) extends Table[AOI](_tableTag, "aois")
    with TimestampFields with OrganizationFkFields with UserFkFields {

  def * = (id, createdAt, modifiedAt, organizationId, createdBy,
    modifiedBy, owner, area, filters) <> (AOI.tupled, AOI.unapply)

  /* Database Fields */
  val id: Rep[UUID] = column[UUID]("id", O.PrimaryKey)
  val createdAt: Rep[Timestamp] = column[Timestamp]("created_at")
  val modifiedAt: Rep[Timestamp] = column[Timestamp]("modified_at")
  val organizationId: Rep[UUID] = column[UUID]("organization_id")
  val createdBy: Rep[String] = column[String]("created_by", O.Length(255,varying=true))
  val modifiedBy: Rep[String] = column[String]("modified_by", O.Length(255,varying=true))
  val owner: Rep[String] = column[String]("owner", O.Length(255,varying=true))

  /* Unique Fields */
  val area: Rep[Projected[MultiPolygon]] = column[Projected[MultiPolygon]]("area")
  val filters: Rep[Json] = column[Json]("filters")

  /* Foreign Keys */
  lazy val organizationsFk = foreignKey("aois_organization_id_fkey", organizationId, Organizations)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val createdByUserFK = foreignKey("aois_created_by_fkey", createdBy, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val modifiedByUserFK = foreignKey("aois_modified_by_fkey", modifiedBy, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val ownerUserFK = foreignKey("aois_owner_fkey", owner, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
}

object AOIs extends TableQuery(tag => new AOIs(tag)) with LazyLogging {
  type TableQuery = Query[AOIs, AOI, Seq]

  implicit val aoiSorter: QuerySorter[AOIs] =
    new QuerySorter(
      new OrganizationFkSort(identity),
      new TimestampSort(identity))

  /** Paginate a `list` result. */
  def page(pageRequest: PageRequest, aois: TableQuery): TableQuery = {
    aois.sort(pageRequest.sort)
      .drop(pageRequest.offset * pageRequest.limit)
      .take(pageRequest.limit)
  }

  /** Yield a paginated list of all [[AOI]]s that a user has access to. */
  def listAOIs(
    pageRequest: PageRequest,
    queryParams: AoiQueryParameters,
    user: User
  )(implicit database: DB): Future[PaginatedResponse[AOI]] = {
    val aoisQ: Query[AOIs, AOI, Seq] = AOIs.filterByOrganization(queryParams.orgParams)
      .filterByUser(queryParams.userParams)
      .filterByTimestamp(queryParams.timestampParams)

    /* Fire the Futures ahead of time */
    val paginated: Future[Seq[AOI]] = database.db.run(page(pageRequest, aoisQ).result)
    val totalQ: Future[Int] = database.db.run(aoisQ.length.result)

    for {
      total <- totalQ
      aois  <- paginated
    } yield {
      val hasNext = (pageRequest.offset + 1) * pageRequest.limit < total
      val hasPrevious = pageRequest.offset > 0

      PaginatedResponse[AOI](
        total, hasPrevious, hasNext, pageRequest.offset, pageRequest.limit, aois
      )
    }
  }

  /** Add an AOI to the database. */
  def insertAOI(aoi: AOI)(implicit database: DB): Future[AOI] =
    database.db.run(AOIs.forceInsert(aoi)).map(_ => aoi)

  /** Get an [[AOI]] given its UUID. */
  def getAOI(aoi: UUID, user: User)(implicit database: DB): OptionT[Future, AOI] =
    OptionT(database.db.run({
      AOIs.filterToSharedOrganizationIfNotInRoot(user)
        .filter(_.id === aoi)
        .result
        .headOption
    }))

  /** Delete an [[AOI]] given its UUID. */
  def deleteAOI(aoi: UUID, user: User)(implicit database: DB): Future[Int] = {
    database.db.run({
      AoisToProjects
        .filter(_.aoiId === aoi)
        .delete
    })

    database.db.run({
      AOIs.filterToSharedOrganizationIfNotInRoot(user)
        .filter(_.id === aoi)
        .delete
    })
  }

  def updateAOI(aoi: AOI, aoiId: UUID, user: User)(implicit database: DB): Future[Int] = {

    val now = new Timestamp((new Date()).getTime)

    val query = AOIs.filterToSharedOrganizationIfNotInRoot(user)
      .filter(_.id === aoiId)
      .map(a => (a.modifiedAt, a.modifiedBy, a.area, a.filters))

    database.db.run(query.update((now, user.id, aoi.area, aoi.filters))).map {
      case 1 => 1
      case c => throw new IllegalStateException(s"Error updating project: update result expected to be 1, was $c")
    }
  }

}
