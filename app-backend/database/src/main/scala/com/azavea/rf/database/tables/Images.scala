package com.azavea.rf.database.tables

import com.azavea.rf.database.fields._
import com.azavea.rf.database.query._
import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.datamodel._
import slick.model.ForeignKeyAction
import java.util.UUID
import java.sql.Timestamp
import com.lonelyplanet.akka.http.extensions.{PageRequest, Order}
import scala.concurrent.{Future, ExecutionContext}
import scala.util.{Try, Success, Failure}
import com.typesafe.scalalogging.LazyLogging

class Images(_tableTag: Tag) extends Table[Image](_tableTag, "images")
                                     with ImageFields
                                     with OrganizationFkFields
                                     with UserFkFields
                                     with TimestampFields
                                     with VisibilityField
{
  def * = (id, createdAt, modifiedAt, organizationId, createdBy, modifiedBy, rawDataBytes, visibility, filename, sourceuri, scene, bands, imageMetadata) <> (Image.tupled, Image.unapply)
  /** Maps whole row to an option. Useful for outer joins. */
  def ? = (Rep.Some(id), Rep.Some(createdAt), Rep.Some(modifiedAt), Rep.Some(organizationId), Rep.Some(createdBy), Rep.Some(modifiedBy), Rep.Some(rawDataBytes), Rep.Some(visibility), Rep.Some(filename), Rep.Some(sourceuri), Rep.Some(scene), Rep.Some(bands), Rep.Some(imageMetadata)).shaped.<>({r=>import r._; _1.map(_=> Image.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get, _7.get, _8.get, _9.get, _10.get, _11.get, _12.get, _13.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

  val id: Rep[java.util.UUID] = column[java.util.UUID]("id", O.PrimaryKey)
  val createdAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created_at")
  val modifiedAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("modified_at")
  val organizationId: Rep[java.util.UUID] = column[java.util.UUID]("organization_id")
  val createdBy: Rep[String] = column[String]("created_by", O.Length(255,varying=true))
  val modifiedBy: Rep[String] = column[String]("modified_by", O.Length(255,varying=true))
  val rawDataBytes: Rep[Int] = column[Int]("raw_data_bytes")
  val visibility: Rep[Visibility] = column[Visibility]("visibility")
  val filename: Rep[String] = column[String]("filename")
  val sourceuri: Rep[String] = column[String]("sourceuri")
  val scene: Rep[java.util.UUID] = column[java.util.UUID]("scene")
  val bands: Rep[List[String]] = column[List[String]]("bands", O.Length(2147483647,varying=false))
  val imageMetadata: Rep[Map[String, Any]] = column[Map[String, Any]]("image_metadata", O.Length(2147483647,varying=false))

  /** Foreign key referencing Organizations (database name images_organization_id_fkey) */
  lazy val organizationsFk = foreignKey("images_organization_id_fkey", organizationId, Organizations)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  /** Foreign key referencing Scenes (database name images_scene_fkey) */
  lazy val scenesFk = foreignKey("images_scene_fkey", scene, Scenes)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  /** Foreign key referencing Users (database name images_created_by_fkey) */
  lazy val createdByUserFK = foreignKey("images_created_by_fkey", createdBy, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  /** Foreign key referencing Users (database name images_modified_by_fkey) */
  lazy val modifiedByUserFK = foreignKey("images_modified_by_fkey", modifiedBy, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)

}

object Images extends TableQuery(tag => new Images(tag)) with LazyLogging {
  type TableQuery = Query[Images, Images#TableElementType, Seq]

  implicit class withImagesDefaultQuery[M, U, C[_]](images: Images.TableQuery) extends
      ImagesDefaultQuery[M, U, C](images)

  /** Insert one image into the database
    *
    * @param image Image case class for image to insert into database
    */
  def insertImage(image: Image)
    (implicit database: DB, ec: ExecutionContext): Future[Try[Image]] = {
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
    (implicit database: DB, ec: ExecutionContext): Future[Option[Image]] = {

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
    (implicit database: DB, ec: ExecutionContext): Future[PaginatedResponse[Image]] = {

    val images = Images.filterByOrganization(combinedParams.orgParams)
      .filterByTimestamp(combinedParams.timestampParams)
      .filterByImageParams(combinedParams.imageParams)

    val imagesQueryResult = database.db.run {
      val action = images.page(pageRequest).result
      logger.debug(s"Query for images -- SQL: ${action.statements.headOption}")
      action
    }
    val totalImagesQuery = database.db.run {
      val action = images.length.result
      logger.debug(s"Total Query for images -- SQL: ${action.statements.headOption}")
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
    * @param image Image updated image
    * @param imageId ID of image in database to update
    * @param user User user doing the updating
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
  def updateImage(image: Image, imageId: UUID, user: User)
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
        image.filename, image.sourceUri, image.scene, image.bands, image.imageMetadata
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

class ImagesDefaultQuery[M, U, C[_]](images: Images.TableQuery) {
  def filterByImageParams(imageParams: ImageQueryParameters): Images.TableQuery = {
    images.filter{ image =>
      val imageFilterConditions = List(
        imageParams.minRawDataBytes.map(image.rawDataBytes > _),
        imageParams.maxRawDataBytes.map(image.rawDataBytes < _)
      )
      imageFilterConditions
        .collect({case Some(criteria)  => criteria})
        .reduceLeftOption(_ && _).getOrElse(true: Rep[Boolean])
    }.filter{ image =>
      imageParams.scene
        .map(image.scene === _)
        .reduceLeftOption(_ || _)
        .getOrElse(true: Rep[Boolean])
    }
  }

  def sort(sortMap: Map[String, Order]): Images.TableQuery = {
    def applySort(query: Images.TableQuery, sortMap: Map[String, Order]): Images.TableQuery = {
      sortMap.headOption match {
        case Some(("createdAt", Order.Asc)) => applySort(query.sortBy(_.createdAt.asc),
                                                         sortMap.tail)
        case Some(("createdAt", Order.Desc)) => applySort(query.sortBy(_.createdAt.desc),
                                                          sortMap.tail)

        case Some(("modifiedAt", Order.Asc)) => applySort(query.sortBy(_.modifiedAt.asc),
                                                          sortMap.tail)
        case Some(("modifiedAt", Order.Desc)) => applySort(query.sortBy(_.modifiedAt.desc),
                                                           sortMap.tail)

        case Some(("organization", Order.Asc)) => applySort(query.sortBy(_.organizationId.asc),
                                                            sortMap.tail)
        case Some(("organization", Order.Desc)) => applySort(query.sortBy(_.organizationId.desc),
                                                             sortMap.tail)

        case Some(("visibility", Order.Asc)) => applySort(query.sortBy(_.visibility.asc),
                                                          sortMap.tail)
        case Some(("visibility", Order.Desc)) => applySort(query.sortBy(_.visibility.desc),
                                                           sortMap.tail)

        case Some(("rawDataBytes", Order.Asc)) => applySort(query.sortBy(_.rawDataBytes.asc),
                                                            sortMap.tail)
        case Some(("rawDataBytes", Order.Desc)) => applySort(query.sortBy(_.rawDataBytes.desc),
                                                             sortMap.tail)

        case Some(_) => applySort(query, sortMap.tail)
        case _ => query
      }
    }
    applySort(images, sortMap)
  }

  def page(pageRequest: PageRequest): Images.TableQuery = {
    val sorted = images.sort(pageRequest.sort)
    sorted
      .drop(pageRequest.offset * pageRequest.limit)
      .take(pageRequest.limit)
  }
}
