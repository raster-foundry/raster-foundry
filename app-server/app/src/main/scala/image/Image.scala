package com.azavea.rf.image

import java.sql.Timestamp
import java.util.UUID

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

import com.lonelyplanet.akka.http.extensions.PageRequest
import slick.lifted._

import com.azavea.rf.AkkaSystem
import com.azavea.rf.datamodel.latest.schema.tables._
import com.azavea.rf.datamodel.enums._
import com.azavea.rf.utils.{Database => DB, PaginatedResponse}
import com.azavea.rf.datamodel.driver.ExtendedPostgresDriver


/** Case class to handle creating images via POST request */
case class CreateImage(
  organizationId: UUID,
  rawDataBytes: Int,
  visibility: Visibility,
  filename: String,
  sourceuri: String,
  scene: UUID,
  bands: List[String],
  imageMetadata: Map[String, Any]
) {

  /** Return an ImagesRow after supplying a User ID
    *
    * @param userId String ID for user creating image
    */
  def toImage(userId: String): ImagesRow = {
    val now = new Timestamp((new java.util.Date()).getTime())

    ImagesRow(
      UUID.randomUUID, // primary key
      now, // createdAt
      now, // modifiedAt
      organizationId,
      userId, // createdBy: String,
      userId, // modifiedBy: String,
      rawDataBytes,
      visibility,
      filename,
      sourceuri,
      scene,
      bands,
      imageMetadata
    )
  }
}



object ImageService extends AkkaSystem.LoggerExecutor {

  type ImagesQuery = Query[Images, Images#TableElementType, Seq]
  import ImageFilters._

  import ExtendedPostgresDriver.api._

  /** Insert one image into the database
    *
    * @param image ImagesRow case class for image to insert into database
    */
  def insertImage(image: ImagesRow)
    (implicit database: DB, ec: ExecutionContext): Future[Try[ImagesRow]] = {
    database.db.run {
      Images.forceInsert(image).asTry
    } map {
      case Success(_) => Success(image)
      case Failure(e) => Failure(e)
    }
  }

  /** Get an image given an ID
    *
    * @param imageID UUID ID of image to get from database
    */
  def getImage(imageId: UUID)
    (implicit database: DB, ec: ExecutionContext): Future[Option[ImagesRow]] = {

    database.db.run {
      Images.filter(_.id === imageId).result.headOption
    }
  }

  /** Retrieve a list of images from database given a page request and query parameters
    *
    * @param pageRequest PageRequest pagination class to return paginated results
    * @param combinedParams CombinedImagequeryparams query parameters that can be applied to images
    */
  def listImages(pageRequest: PageRequest, combinedParams: CombinedImageQueryParams)
    (implicit database: DB, ec: ExecutionContext): Future[PaginatedResponse[ImagesRow]] = {

    val images = Images.filterByOrganization(combinedParams.orgParams)
      .filterByTimestamp(combinedParams.timestampParams)
      .filterByImageParams(combinedParams.imageParams)

    val imagesQueryResult = database.db.run {
      val action = images.page(pageRequest).result
      log.debug(s"Query for images -- SQL: ${action.statements.headOption}")
      action
    }
    val totalImagesQuery = database.db.run {
      val action = images.length.result
      log.debug(s"Total Query for images -- SQL: ${action.statements.headOption}")
      action
    }

    for {
      totalImages <- totalImagesQuery
      images <- imagesQueryResult
    } yield {
      val hasNext = (pageRequest.offset + 1) * pageRequest.limit < totalImages // 0 indexed page offset
      val hasPrevious = pageRequest.offset > 0
      PaginatedResponse(totalImages, hasPrevious, hasNext,
        pageRequest.offset, pageRequest.limit, images)
    }
  }

  /** Delete an image from the database
    *
    * @param imageId UUID id of image to delete from database
    */
  def deleteImage(imageId: UUID)(implicit database: DB, ec: ExecutionContext): Future[Int] = {
    database.db.run {
      Images.filter(_.id === imageId).delete
    }
  }

  /** Update an image in the database
    *
    * @param image ImagesRow updated image
    * @param imageId ID of image in database to update
    * @param user UsersRow user doing the updating
    *
    * The following fields can be updated -- others will be ignored
    *  - rawDataBytes
    *  - visibility
    *  - filename
    *  - sourceuri
    *  - scene
    *  - bands
    *  - imageMetadata
    */
  def updateImage(image: ImagesRow, imageId: UUID, user: UsersRow)
    (implicit database: DB, ec: ExecutionContext): Future[Try[Int]] = {

    val updateTime = new Timestamp((new java.util.Date()).getTime())

    val updateImageQuery = for {
      updateImage <- Images.filter(_.id === imageId)
    } yield (
      updateImage.modifiedAt, updateImage.modifiedBy, updateImage.rawDataBytes,
      updateImage.visibility, updateImage.filename, updateImage.sourceuri,
      updateImage.scene, updateImage.bands, updateImage.imageMetadata
    )

    database.db.run {
      updateImageQuery.update((
        updateTime, user.id, image.rawDataBytes, image.visibility,
        image.filename, image.sourceuri, image.scene, image.bands, image.imageMetadata
      )).asTry
    } map {
      case Success(result) => {
        result match {
          case 1 => Success(1)
          case _ => Failure(new Exception("Error while updating image"))
        }
      }
      case Failure(e) => Failure(e)
    }
  }
}
