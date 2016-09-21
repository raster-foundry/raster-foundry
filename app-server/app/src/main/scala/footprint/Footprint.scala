package com.azavea.rf.footprint


import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import java.sql.Timestamp
import java.util.UUID
import spray.json._

import geotrellis.vector.io._
import geotrellis.vector.Geometry
import geotrellis.slick.Projected

import com.azavea.rf.utils.Database
import com.azavea.rf.datamodel.latest.schema.tables._
import com.azavea.rf.AkkaSystem
import com.azavea.rf.utils.UserErrorException


case class FootprintWithGeojsonCreate(
  organizationId: UUID, createdAt: Timestamp, modifiedAt: Timestamp, multipolygon: JsValue
) {
  def toFootprintsRow(): Try[FootprintsRow] = {
    Try(
      FootprintsRow(
        UUID.randomUUID(), organizationId, createdAt,
        modifiedAt, Projected(multipolygon.convertTo[Geometry], 3857)
      )
    )
  }
}


case class FootprintWithGeojson(
  id: UUID, organizationId: UUID, createdAt: Timestamp, modifiedAt: Timestamp, multipolygon: JsValue
) {

  def this(footprint: FootprintsRow) = this(
    footprint.id, footprint.organizationId, footprint.createdAt,
    footprint.modifiedAt, footprint.multipolygon.geom.toGeoJson.toJson
  )

  def toFootprintsRow(): FootprintsRow = {
    FootprintsRow(
      id, organizationId, createdAt,
      modifiedAt, Projected(multipolygon.convertTo[Geometry], 3857)
    )
  }
}


object FootprintWithGeojson {
  def apply(footprint: FootprintsRow) = new FootprintWithGeojson(footprint)
}


object FootprintService extends AkkaSystem.LoggerExecutor {

  /**
    * Insert a footprint into the database
    *
    * Converts geojson multipolygon to Projected[Geometry]
    *
    * @param footprint FootprintWithGeojsonCreate
    */
  def insertFootprint(footprint: FootprintWithGeojsonCreate)(
    implicit database: Database, ec: ExecutionContext): Future[Try[FootprintWithGeojson]] = {

    import database.driver.api._

    footprint.toFootprintsRow() match {
      case Success(footprintRow) => {
        database.db.run {
          Footprints.forceInsert(footprintRow).asTry
        } map {
          case Success(_) => Success(FootprintWithGeojson(footprintRow))
          case Failure(e) => Failure(e)
            // TODO catch cases where geojson is not formatted correctly, and return a helpful
            // error message through the API instead of a 500
            // eg wrong geometry type
        }
      }
      case Failure(e) => e match {
        case e: DeserializationException => {
          val message = e.getMessage();
          Future(
            Failure(
              new UserErrorException(
                "The request content was malformed:\n" +
                  s"Error when parsing member 'multipolygon':\n$message"
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

  def getFootprints()(implicit database: Database, ec: ExecutionContext):
      Future[Seq[FootprintWithGeojson]] = {
    import database.driver.api._

    database.db.run {
      Footprints.result
    } map {
      footprints => footprints.map(
        footprint => FootprintWithGeojson(footprint)
      )
    }
  }
}
