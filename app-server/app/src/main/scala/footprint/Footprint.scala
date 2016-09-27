package com.azavea.rf.footprint


import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import java.sql.Timestamp
import java.util.UUID
import spray.json._
import com.lonelyplanet.akka.http.extensions.PageRequest

import geotrellis.vector.io._
import geotrellis.vector.Geometry
import geotrellis.slick.Projected

import com.azavea.rf.AkkaSystem
import com.azavea.rf.datamodel.latest.schema.tables._
import com.azavea.rf.datamodel.driver.ExtendedPostgresDriver
import com.azavea.rf.utils.{Database => DB, PaginatedResponse}
import com.azavea.rf.utils.UserErrorException


case class FootprintWithGeojsonCreate(
  organizationId: UUID, multipolygon: JsObject, sceneId: UUID
) {
  def toFootprintsRow(): Try[FootprintsRow] = {
    val now = new Timestamp((new java.util.Date()).getTime())
    Try(
      FootprintsRow(
        UUID.randomUUID(), organizationId, now,
        now, Projected(multipolygon.convertTo[Geometry], 3857),
        sceneId
      )
    )
  }
}


case class FootprintWithGeojson(
  id: UUID, organizationId: UUID, createdAt: Timestamp, modifiedAt: Timestamp, multipolygon: JsObject, sceneId: UUID
) {

  def this(footprint: FootprintsRow) = this(
    footprint.id, footprint.organizationId, footprint.createdAt,
    footprint.modifiedAt, footprint.multipolygon.geom.toGeoJson.parseJson.asJsObject,
    footprint.sceneId
  )

  def toFootprintsRow(): FootprintsRow = {
    FootprintsRow(
      id, organizationId, createdAt,
      modifiedAt, Projected(multipolygon.convertTo[Geometry], 3857),
      sceneId
    )
  }
}


object FootprintWithGeojson {
  def apply(footprint: FootprintsRow) = new FootprintWithGeojson(footprint)
}


object FootprintService extends AkkaSystem.LoggerExecutor {

  import FootprintFilters._
  import ExtendedPostgresDriver.api._

  /**
    * List footprints
    */
  def listFootprints(pageRequest: PageRequest, combinedParams: CombinedFootprintQueryParams)(
    implicit database: DB, ec: ExecutionContext): Future[PaginatedResponse[FootprintWithGeojson]] = {
    val footprints = Footprints.filterByOrganization(combinedParams.orgParams)
      .filterByTimestamp(combinedParams.timestampParams)
      .filterByFootprintParams(combinedParams.footprintParams)

    val footprintsQueryResult = database.db.run {
      val action = footprints.page(pageRequest).result
      log.debug(s"Query for footprints == SQL: ${action.statements.headOption}")
      action
    } map {
      footprints => footprints.map(
        footprint => FootprintWithGeojson(footprint)
      )
    }

    val totalFootprintsQuery = database.db.run {
      val action = footprints.length.result
      log.debug(s"Total Query for footprints -- SQL: ${action.statements.headOption}")
      action
    }

    for {
      totalFootprints <- totalFootprintsQuery
      footprints <- footprintsQueryResult
    } yield {
      val hasNext = (pageRequest.offset + 1) * pageRequest.limit < totalFootprints // 0 indexed page offset
      val hasPrevious = pageRequest.offset > 0
      PaginatedResponse(
        totalFootprints, hasPrevious, hasNext, pageRequest.offset, pageRequest.limit, footprints
      )
    }
  }

  /**
    * Insert a footprint into the database
    *
    * @param footprint FootprintWithGeojsonCreate
    */
  def insertFootprint(footprint: FootprintWithGeojsonCreate)(
    implicit database: DB, ec: ExecutionContext): Future[Try[FootprintWithGeojson]] = {
    footprint.toFootprintsRow() match {
      case Success(footprintRow) => {
        database.db.run {
          Footprints.forceInsert(footprintRow).asTry
        } map {
          case Success(_) => Success(FootprintWithGeojson(footprintRow))
          case Failure(e) => Failure(e)
        }
      }
      /** Additional failure handling logic is necessary because initial deserialization
        * doesn't handle conversion of geojson to Projected[Geometry], which is the
        * internal format
        */
      case Failure(e) => e match {
        case e: DeserializationException => {
          Future(
            Failure(
              new UserErrorException(
                "The request content was malformed:\n" +
                  s"Error when parsing member 'multipolygon':\n${e.getMessage()}"
              )
            )
          )
        }
        case _ =>
          Future(
            Failure(
              new UserErrorException(
                "The request content was malformed:\n" +
                  "Object member 'multipolygon' must be a valid geojson multipolygon"
              )
            )
          )
      }
    }
  }

  /**
    * Get a footprint
    *
    * @param id java.util.UUID
    */
  def getFootprint(id: java.util.UUID)(
    implicit database: DB, ec: ExecutionContext):
      Future[Option[FootprintWithGeojson]] = {
    database.db.run {
      Footprints.filter(_.id === id).result.headOption
    } map {
      case Some(footprint) => Some(FootprintWithGeojson(footprint))
      case _ => None
    }
  }

  /**
    * Update a footprint, and sets modifiedAt to the current time
    *
    * Editable attributes:
    * multipolygon Projected[Geometry] geometry of type multipolygon
    *
    * @param fpUpdate FootprintWithGeojson
    * @param id java.utl.UUID
    */
  def updateFootprint(fpUpdate: FootprintWithGeojson, id: java.util.UUID)(
    implicit database: DB): Future[Try[Int]] = {
    val footprintRow = fpUpdate.toFootprintsRow()
    val now = new Timestamp((new java.util.Date()).getTime())

    val query = for {
      footprint <- Footprints.filter(_.id === id)
    } yield (footprint.modifiedAt, footprint.multipolygon)
    database.db.run {
      query.update((now, footprintRow.multipolygon)).asTry
    }
  }

  def deleteFootprint(fpId: java.util.UUID)(implicit database: DB): Future[Int] = {
    database.db.run {
      Footprints.filter(_.id === fpId).delete
    }
  }
}
