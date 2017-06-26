package com.azavea.rf.database.tables

import com.azavea.rf.database.fields._
import com.azavea.rf.database.query._
import com.azavea.rf.database.sort._
import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.datamodel._
import slick.model.ForeignKeyAction
import java.util.UUID
import java.sql.Timestamp
import com.lonelyplanet.akka.http.extensions.PageRequest
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import com.typesafe.scalalogging.LazyLogging

import io.circe.Json

class Images(_tableTag: Tag) extends Table[Image](_tableTag, "images")
    with ImageFields
    with OrganizationFkFields
    with UserFkVisibleFields
    with TimestampFields
{
  def * = (id, createdAt, modifiedAt, organizationId, createdBy, modifiedBy, owner,
    rawDataBytes, visibility, filename, sourceuri, scene, imageMetadata,
    resolutionMeters, metadataFiles) <> (Image.tupled, Image.unapply)

  val id: Rep[java.util.UUID] = column[java.util.UUID]("id", O.PrimaryKey)
  val createdAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created_at")
  val modifiedAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("modified_at")
  val organizationId: Rep[java.util.UUID] = column[java.util.UUID]("organization_id")
  val createdBy: Rep[String] = column[String]("created_by", O.Length(255,varying=true))
  val modifiedBy: Rep[String] = column[String]("modified_by", O.Length(255,varying=true))
  val owner: Rep[String] = column[String]("owner", O.Length(255,varying=true))
  val rawDataBytes: Rep[Long] = column[Long]("raw_data_bytes")
  val visibility: Rep[Visibility] = column[Visibility]("visibility")
  val filename: Rep[String] = column[String]("filename")
  val sourceuri: Rep[String] = column[String]("sourceuri")
  val scene: Rep[java.util.UUID] = column[java.util.UUID]("scene")
  val imageMetadata: Rep[Json] = column[Json]("image_metadata", O.Length(2147483647,varying=false))
  val resolutionMeters: Rep[Float] = column[Float]("resolution_meters")
  val metadataFiles: Rep[List[String]] = column[List[String]]("metadata_files", O.Length(2147483647,varying=false), O.Default(List.empty))

  /** Foreign key referencing Organizations (database name images_organization_id_fkey) */
  lazy val organizationsFk = foreignKey("images_organization_id_fkey", organizationId, Organizations)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  /** Foreign key referencing Scenes (database name images_scene_fkey) */
  lazy val scenesFk = foreignKey("images_scene_fkey", scene, Scenes)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  /** Foreign key referencing Users (database name images_created_by_fkey) */
  lazy val createdByUserFK = foreignKey("images_created_by_fkey", createdBy, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  /** Foreign key referencing Users (database name images_modified_by_fkey) */
  lazy val modifiedByUserFK = foreignKey("images_modified_by_fkey", modifiedBy, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val ownerUserFK = foreignKey("images_owner_fkey", owner, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)

}

object Images extends TableQuery(tag => new Images(tag)) with LazyLogging {
  type TableQuery = Query[Images, Images#TableElementType, Seq]

  implicit val imagesSorter: QuerySorter[Images] =
    new QuerySorter(
      new ImageFieldsSort(identity[Images]),
      new OrganizationFkSort(identity[Images]),
      new VisibilitySort(identity[Images]),
      new TimestampSort(identity[Images]))

  implicit class withImagesDefaultQuery[M, U, C[_]](images: Images.TableQuery) extends
      ImagesDefaultQuery[M, U, C](images)

  /** Insert one image into the database with its bands
    *
    * @param imageBanded Image case class for image to insert into database
    * @param user User object inserting the image
    */
  def insertImage(imageBanded: Image.Banded, user: User)
                 (implicit database: DB): Future[Image.WithRelated] = {
    val image = imageBanded.toImage(user)
    val bands = imageBanded.bands map { _.toBand(image.id)}
    val insertAction = (
      for {
        imageInsert <- (Images returning Images).forceInsert(image)
        bandsInsert <- (Bands returning Bands).forceInsertAll(bands)
      } yield (imageInsert, bandsInsert)).transactionally
    database.db.run {
      insertAction
    } map {
      case (im: Image, bs: Seq[Band]) => im.withRelatedFromComponents(bs)
    }
  }

  /** Get an image given an ID
    *
    * @param imageId UUID ID of image to get from database
    * @param user    Results will be limited to user's organization
    */
  def getImage(imageId: UUID, user: User)
              (implicit database: DB): Future[Option[Image.WithRelated]] = {

    val fetchAction = for {
      imageFetch <- Images
                      .filterToSharedOrganizationIfNotInRoot(user)
                      .filter(_.id === imageId)
                      .result
                      .headOption
      bandsFetch <- Bands.filter(_.imageId === imageId).result
    } yield (imageFetch, bandsFetch)
    database.db.run {
      fetchAction
    } map {
      case (Some(im), bs) => Some(im.withRelatedFromComponents(bs))
      case _ => None
    }
  }

  /** Retrieve a list of images with bands from database given a
    * page request and query parameters
    *
    * @param pageRequest PageRequest pagination class to return paginated results
    * @param combinedParams CombinedImagequeryparams query parameters that can be applied to images
    */
  def listImages(pageRequest: PageRequest, combinedParams: CombinedImageQueryParams, user: User)
                (implicit database: DB): Future[PaginatedResponse[Image.WithRelated]] = {

    val images = Images.filterUserVisibility(user)
      .filterByOrganization(combinedParams.orgParams)
      .filterByTimestamp(combinedParams.timestampParams)
      .filterByImageParams(combinedParams.imageParams)

    val imageBandsJoin = for {
      imageList <- images.page(pageRequest)
      bands <- Bands if imageList.id === bands.imageId
    } yield (imageList, bands)

    val imagesQueryResult = database.db.run {
      val action = imageBandsJoin.result
      logger.debug(s"Query for images -- SQL: ${action.statements.headOption}")
      action
    } map { records =>
      Image.WithRelated.fromRecords(records)
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
                        pageRequest.offset, pageRequest.limit, images.toSeq)
    }
  }

  /** Delete an image from the database
    *
    * @param imageId UUID id of image to delete from database
    * @param user    Results will be limited to user's organization
    */
  def deleteImage(imageId: UUID, user: User)(implicit database: DB): Future[Int] = {
    database.db.run {
      Images
        .filterToSharedOrganizationIfNotInRoot(user)
        .filter(_.id === imageId)
        .delete
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
    *  - imageMetadata
    */
  def updateImage(image: Image.WithRelated, imageId: UUID, user: User)
                 (implicit database: DB): Future[Int] = {

    val updateTime = new Timestamp((new java.util.Date).getTime)
    val bands = image.bands

    val updateImageQuery = for {
      updateImage <- Images
                       .filterToSharedOrganizationIfNotInRoot(user)
                       .filter(_.id === imageId)
    } yield (
      updateImage.modifiedAt, updateImage.modifiedBy, updateImage.rawDataBytes,
      updateImage.visibility, updateImage.filename, updateImage.sourceuri,
      updateImage.scene, updateImage.imageMetadata,
      updateImage.resolutionMeters, updateImage.metadataFiles
    )

    val updateImageAction = updateImageQuery.update(
      (updateTime, user.id, image.rawDataBytes,
       image.visibility, image.filename, image.sourceUri,
       image.scene, image.imageMetadata,
       image.resolutionMeters, image.metadataFiles)
    )

    val deleteOldBandsAction = Bands.filter(_.imageId === image.id).delete

    val createNewBandsAction = Bands.forceInsertAll(bands)

    val updateAction = (for {
      updateIm <- updateImageAction
      deleteBands <- deleteOldBandsAction
      newBands <- createNewBandsAction
    } yield (updateIm, deleteBands, newBands)) .transactionally

    database.db.run {
      updateAction
    } map {
      case (1, _, _) => 1
      case c => throw new IllegalStateException(s"Error updating image: update result expected to be 1, was $c")
    }
  }
}

class ImagesDefaultQuery[M, U, C[_]](images: Images.TableQuery) {
  def filterByImageParams(imageParams: ImageQueryParameters): Images.TableQuery = {
    images.filter{ image =>
      val imageFilterConditions = List(
        imageParams.minRawDataBytes.map(image.rawDataBytes > _),
        imageParams.maxRawDataBytes.map(image.rawDataBytes < _),
        imageParams.minResolution.map(image.resolutionMeters > _),
        imageParams.maxResolution.map(image.resolutionMeters < _)
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

  def page(pageRequest: PageRequest): Images.TableQuery = {
    val sorted = images.sort(pageRequest.sort)
    sorted
      .drop(pageRequest.offset * pageRequest.limit)
      .take(pageRequest.limit)
  }
}
