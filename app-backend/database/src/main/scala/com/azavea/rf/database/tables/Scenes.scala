package com.azavea.rf.database.tables

import com.azavea.rf.database.fields._
import com.azavea.rf.database.query._
import com.azavea.rf.database.sort._
import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.datamodel._

import geotrellis.slick.Projected
import geotrellis.vector.{Geometry, Point, Polygon, Extent}
import com.typesafe.scalalogging.LazyLogging
import com.lonelyplanet.akka.http.extensions.PageRequest

import java.util.UUID
import java.sql.Timestamp
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global


/** Table description of table scenes. Objects of this class serve as prototypes for rows in queries. */
class Scenes(_tableTag: Tag) extends Table[Scene](_tableTag, "scenes")
                                     with SceneFields
                                     with OrganizationFkFields
                                     with UserFkVisibileFields
                                     with TimestampFields
{
  def * = (id, createdAt, createdBy, modifiedAt, modifiedBy, organizationId, ingestSizeBytes, visibility,
    tags, datasource, sceneMetadata, cloudCover, acquisitionDate, thumbnailStatus, boundaryStatus,
    sunAzimuth, sunElevation, name, tileFootprint, dataFootprint, metadataFiles, ingestLocation) <> (Scene.tupled, Scene.unapply)

  val id: Rep[java.util.UUID] = column[java.util.UUID]("id", O.PrimaryKey)
  val createdAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created_at")
  val createdBy: Rep[String] = column[String]("created_by", O.Length(255,varying=true))
  val modifiedAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("modified_at")
  val modifiedBy: Rep[String] = column[String]("modified_by", O.Length(255,varying=true))
  val organizationId: Rep[java.util.UUID] = column[java.util.UUID]("organization_id")
  val ingestSizeBytes: Rep[Int] = column[Int]("ingest_size_bytes")
  val visibility: Rep[Visibility] = column[Visibility]("visibility")
  val tags: Rep[List[String]] = column[List[String]]("tags", O.Length(2147483647,varying=false))
  val datasource: Rep[String] = column[String]("datasource", O.Length(255,varying=true))
  val sceneMetadata: Rep[Map[String, Any]] = column[Map[String, Any]]("scene_metadata", O.Length(2147483647,varying=false))
  val cloudCover: Rep[Option[Float]] = column[Option[Float]]("cloud_cover", O.Default(None))
  val acquisitionDate: Rep[Option[java.sql.Timestamp]] = column[Option[java.sql.Timestamp]]("acquisition_date", O.Default(None))
  val thumbnailStatus: Rep[JobStatus] = column[JobStatus]("thumbnail_status")
  val boundaryStatus: Rep[JobStatus] = column[JobStatus]("boundary_status")
  val sunAzimuth: Rep[Option[Float]] = column[Option[Float]]("sun_azimuth", O.Default(None))
  val sunElevation: Rep[Option[Float]] = column[Option[Float]]("sun_elevation", O.Default(None))
  val name: Rep[String] = column[String]("name", O.Length(255,varying=true))
  val tileFootprint: Rep[Option[Projected[Geometry]]] = column[Option[Projected[Geometry]]]("tile_footprint", O.Length(2147483647,varying=false), O.Default(None))
  val dataFootprint: Rep[Option[Projected[Geometry]]] = column [Option[Projected[Geometry]]]("data_footprint", O.Length(2147483647,varying=false), O.Default(None))
  val metadataFiles: Rep[List[String]] = column[List[String]]("metadata_files", O.Length(2147483647,varying=false), O.Default(List.empty))
  val ingestLocation: Rep[Option[String]] = column[Option[String]]("ingest_location", O.Default(None))

  /** Foreign key referencing Organizations (database name scenes_organization_id_fkey) */
  lazy val organizationsFk = foreignKey("scenes_organization_id_fkey", organizationId, Organizations)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val createdByUserFK = foreignKey("scenes_created_by_fkey", createdBy, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val modifiedByUserFK = foreignKey("scenes_modified_by_fkey", modifiedBy, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)

  /** Uniqueness Index over (name,organizationId,datasource) (database name scene_name_org_datasource) */
  val index1 = index("scene_name_org_datasource", (name, organizationId, datasource), unique=true)
}

/** Collection-like TableQuery object for table Scenes */
object Scenes extends TableQuery(tag => new Scenes(tag)) with LazyLogging {
  type TableQuery = Query[Scenes, Scene, Seq]
  type JoinQuery = Query[ScenesWithRelatedFields, (Scene, Option[Image], Option[Band], Option[Thumbnail]), Seq]
  type GridAggregationFields = (ConstColumn[ConstColumn[Projected[Polygon]]], Rep[Int])
  type ScenesWithRelatedFields = (Scenes, Rep[Option[Images]], Rep[Option[Bands]], Rep[Option[Thumbnails]])

  implicit val scenesSorter: QuerySorter[Scenes] =
    new QuerySorter(
      new SceneFieldsSort(identity[Scenes]),
      new OrganizationFkSort(identity[Scenes]),
      new TimestampSort(identity[Scenes]))

  implicit val scenesWithRelatedSorter: QuerySorter[ScenesWithRelatedFields] =
    new QuerySorter(
      new SceneFieldsSort(_._1),
      new OrganizationFkSort(_._1),
      new TimestampSort(_._1))


  implicit class withScenesTableQuery[M, U, C[_]](scenes: Scenes.TableQuery) extends
      ScenesTableQuery[M, U, C](scenes)

  implicit class withScenesJoinQuery[M, U, C[_]](scenes: Scenes.JoinQuery) extends
      ScenesJoinQuery[M, U, C](scenes)

  val datePart = SimpleFunction.binary[String, Option[Timestamp], Int]("date_part")


  /** Insert a scene into the database
    *
    * @param sceneCreate scene to create
    * @param user User user creating scene
    *
    * This implementation allows a user to post a scene with thumbnails, and
    * images which are all created in a single transaction
    */
  def insertScene(sceneCreate: Scene.Create, user: User)
                 (implicit database: DB): Future[Scene.WithRelated] = {

    val scene = sceneCreate.toScene(user.id)
    val thumbnails = sceneCreate.thumbnails.map(_.toThumbnail(user.id))
    val imageSeq = (sceneCreate.images map { im: Image.Banded => im.toImage(user.id) }).zipWithIndex
    val bandsSeq = imageSeq map { case (im: Image, ind: Int) =>
      sceneCreate.images(ind).bands map { bd => bd.toBand(im.id) }
    }

    val imageBandInsert = DBIO.sequence(
      imageSeq.zip(bandsSeq) map { case ((im: Image, _: Int), bs: Seq[Band]) =>
        // == because we're filtering a sequence, not a table
        for {
          imageInsert <- (Images returning Images).forceInsert(im)
          bandInserts <- (Bands returning Bands).forceInsertAll(bs)
        } yield (imageInsert, bandInserts)
      }
    )

    val actions = for {
      scenesInsert <- Scenes.forceInsert(scene)
      thumbnailsInsert <- Thumbnails.forceInsertAll(thumbnails)
      imageInsert <- imageBandInsert
    } yield (scenesInsert, thumbnailsInsert, imageInsert)

    database.db.run {
      actions.transactionally
    } map { case (_, _, imSeq: Seq[Tuple2[Image, Seq[Band]]]) =>
        val imagesWithRelated = imSeq.map({case (im: Image, bs: Seq[Band]) => im.withRelatedFromComponents(bs)})
      scene.withRelatedFromComponents(imagesWithRelated, thumbnails)
    }
  }


  /** Retrieve a single scene from the database
    *
    * @param sceneId java.util.UUID ID of scene to query with
    */
  def getScene(sceneId: UUID)
              (implicit database: DB): Future[Option[Scene.WithRelated]] = {

    database.db.run {
      val action = Scenes
        .filter(_.id === sceneId)
        .joinWithRelated
        .result
      logger.debug(s"Total Query for scenes -- SQL: ${action.statements.headOption}")
      action
    } map { result =>
      Scene.WithRelated.fromRecords(result).headOption
    }
  }

  /** Get scenes given a page request and query parameters */
  def listScenes(pageRequest: PageRequest, combinedParams: CombinedSceneQueryParams, user: User)
                (implicit database: DB): Future[PaginatedResponse[Scene.WithRelated]] = {

    val scenesQueryResult = database.db.run {
      val action = Scenes.filterUserVisibility(user)
          .joinWithRelated
          .page(combinedParams, pageRequest)
          .result
      logger.debug(s"Paginated Query for scenes -- SQL: ${action.statements.headOption}")
      action
    } map { Scene.WithRelated.fromRecords }

    val totalScenesQueryResult = database.db.run {
      val action = Scenes
        .filterByOrganization(combinedParams.orgParams)
        .filterByUser(combinedParams.userParams)
        .filterByTimestamp(combinedParams.timestampParams)
        .filterBySceneParams(combinedParams.sceneParams)
        .filterByImageParams(combinedParams.imageQueryParameters)
        .length
        .result
      logger.debug(s"Total Query for scenes -- SQL: ${action.statements.headOption}")
      action
    }

    for {
      totalScenes <- totalScenesQueryResult
      scenes <- scenesQueryResult
    } yield {
      val hasNext = (pageRequest.offset + 1) * pageRequest.limit < totalScenes // 0 indexed page offset
      val hasPrevious = pageRequest.offset > 0
      PaginatedResponse(totalScenes, hasPrevious, hasNext,
        pageRequest.offset, pageRequest.limit, scenes.toSeq)
    }
  }

  def listScenesAutoOrder(projectId: UUID)(implicit database: DB) = database.db.run {
    Scenes
      .filter { scene =>
        scene.id in ScenesToProjects.filter(_.projectId === projectId).map(_.sceneId)
      }.sortBy { scene =>
        (scene.acquisitionDate.desc.nullsLast, scene.cloudCover.desc.nullsLast)
      }.result
  }

  def sceneGrid(params: CombinedGridQueryParams, bboxes: Seq[Projected[Polygon]])(implicit database: DB) = {
    val filteredScenes = Scenes
      .filterByOrganization(params.orgParams)
      .filterByUser(params.userParams)
      .filterByTimestamp(params.timestampParams)
      .filterByImageParams(params.imageParams)
      .filterByGridParams(params.gridParams)

    val actions = for {
      bbox <- bboxes
    } yield {
      database.db.run {
        filteredScenes.filter { scene =>
          scene.dataFootprint.intersects(bbox)
        }.length.result
      }
    }
    actions
  }

  def aggregateScenes(combinedParams: CombinedGridQueryParams, bbox: Projected[Polygon], grid: Seq[Projected[Polygon]])
                     (implicit database: DB)
      : Future[List[(Projected[Polygon], Int)]] = {
    val filteredScenes = Scenes
      .filterByOrganization(combinedParams.orgParams)
      .filterByUser(combinedParams.userParams)
      .filterByTimestamp(combinedParams.timestampParams)
      .filterByImageParams(combinedParams.imageParams)
      .filterByGridParams(combinedParams.gridParams)


    val bboxFilteredScenes = filteredScenes.filter { scene =>
      scene.dataFootprint.intersects(bbox)
    }

    val gridWithIndex = grid.zipWithIndex
    val action = gridWithIndex.map { case (cell, idx) =>
      bboxFilteredScenes.filter {
        scene => scene.dataFootprint.intersects(cell)
      } map {
        (idx, _)
      }
    }.reduce(_ union _).result
    logger.debug(s"Aggregated Query for scenes -- SQL: ${action.statements.headOption}")

    database.db.run {
      action
    } map {
      res: Seq[(Int, Scene)] => res.map(_._1)
    } map {
      res =>
      res.groupBy(identity(_)).map {
        case (idx, list) =>
          val gridmap = gridWithIndex.map{case (x, y) => (y, x)}.toMap
          (gridmap(idx), list.length)
      }.toList
    }
  }


  /** Delete a scene from the database
    *
    * @param sceneId java.util.UUID ID of scene to delete
    */
  def deleteScene(sceneId: UUID)(implicit database: DB): Future[Int] = {
    database.db.run {
      Scenes.filter(_.id === sceneId).delete
    }
  }

  /** Update a scene in the database
    *
    * Allows updating the scene from a user -- does not allow a user to update
    * createdBy or createdAt fields
    *
    * @param scene Scene scene to use to update the database
    * @param sceneId java.util.UUID ID of scene to update
    * @param user User user performing the update
    */
  def updateScene(scene: Scene, sceneId: UUID, user: User)
                 (implicit database: DB): Future[Int] = {

    val updateTime = new Timestamp((new java.util.Date).getTime)

    val updateSceneQuery = for {
      updateScene <- Scenes.filterToOwner(user).filter(_.id === sceneId)
    } yield (
      updateScene.modifiedAt, updateScene.modifiedBy, updateScene.ingestSizeBytes,
      updateScene.datasource, updateScene.cloudCover,  updateScene.acquisitionDate,
      updateScene.tags, updateScene.sceneMetadata, updateScene.thumbnailStatus,
      updateScene.boundaryStatus, updateScene.name, updateScene.tileFootprint,
      updateScene.dataFootprint, updateScene.metadataFiles, updateScene.ingestLocation
    )
    database.db.run {
      updateSceneQuery.update((
        updateTime, user.id, scene.ingestSizeBytes,
        scene.datasource, scene.cloudCover, scene.acquisitionDate,
        scene.tags, scene.sceneMetadata, scene.thumbnailStatus,
        scene.boundaryStatus, scene.name, scene.tileFootprint,
        scene.dataFootprint, scene.metadataFiles, scene.ingestLocation
      ))
    } map {
      case 1 => 1
      case _ => throw new IllegalStateException("Error while updating scene")
    }
  }
}


class ScenesTableQuery[M, U, C[_]](scenes: Scenes.TableQuery) extends LazyLogging {
  import Scenes.datePart


  def filterBySceneParams(sceneParams: SceneQueryParameters): Scenes.TableQuery = {
    val filteredScenes = scenes.filter{ scene =>
      val sceneFilterConditions = List(
        sceneParams.maxAcquisitionDatetime.map(scene.acquisitionDate < _),
        sceneParams.minAcquisitionDatetime.map(scene.acquisitionDate > _),
        sceneParams.maxCloudCover.map(scene.cloudCover < _),
        sceneParams.minCloudCover.map(scene.cloudCover > _),
        sceneParams.minSunAzimuth.map(scene.sunAzimuth > _),
        sceneParams.maxSunAzimuth.map(scene.sunAzimuth < _),
        sceneParams.minSunElevation.map(scene.sunElevation > _),
        sceneParams.maxSunElevation.map(scene.sunElevation < _),
        sceneParams.pointGeom.map(scene.dataFootprint.intersects(_))
      )
      sceneFilterConditions
        .collect({case Some(criteria)  => criteria})
        .reduceLeftOption(_ && _)
        .getOrElse(Some(true): Rep[Option[Boolean]])
    }.filter { scene =>
      sceneParams.month
        .map(datePart("month", scene.acquisitionDate) === _)
        .reduceLeftOption(_ || _)
        .getOrElse(true: Rep[Boolean])
    }.filter { scene =>
      sceneParams.minDayOfMonth
        .map(datePart("day", scene.acquisitionDate) >= _)
        .reduceLeftOption(_ || _)
        .getOrElse(true: Rep[Boolean])
    }.filter { scene =>
      sceneParams.maxDayOfMonth
        .map(datePart("day", scene.acquisitionDate) <= _)
        .reduceLeftOption(_ || _)
        .getOrElse(true: Rep[Boolean])
    }.filter { scene =>
      sceneParams.datasource
        .map(scene.datasource === _)
        .reduceLeftOption(_ || _)
        .getOrElse(true: Rep[Boolean])
    }.filter { scene =>
      sceneParams.bboxPolygon match {
        case Some(b) => b.map(scene.dataFootprint.intersects(_))
          .reduceLeftOption(_ || _)
          .getOrElse(Some(true): Rep[Option[Boolean]])
        case _ => Some(true): Rep[Option[Boolean]]
      }
    }.filter { scene =>
      sceneParams.ingested
        .map(scene.ingestLocation.isDefined === _)
        .reduceLeftOption(_ || _)
        .getOrElse(true: Rep[Boolean])
    }

    sceneParams.project match {
      case Some(projectId) => {
        filteredScenes.filter { scene =>
          scene.id in ScenesToProjects.filter(_.projectId === projectId).map(_.sceneId)
        }
      }
      case _ => {
        filteredScenes
      }
    }
  }

  def filterByGridParams(gridParams: GridQueryParameters): Scenes.TableQuery = {
    val filteredScenes = scenes.filter{ scene =>
      val sceneFilterConditions = List(
        gridParams.maxAcquisitionDatetime.map(scene.acquisitionDate < _),
        gridParams.minAcquisitionDatetime.map(scene.acquisitionDate > _),
        gridParams.maxCloudCover.map(scene.cloudCover < _),
        gridParams.minCloudCover.map(scene.cloudCover > _),
        gridParams.minSunAzimuth.map(scene.sunAzimuth > _),
        gridParams.maxSunAzimuth.map(scene.sunAzimuth < _),
        gridParams.minSunElevation.map(scene.sunElevation > _),
        gridParams.maxSunElevation.map(scene.sunElevation < _)
      )
      sceneFilterConditions
        .collect({case Some(criteria)  => criteria})
        .reduceLeftOption(_ && _)
        .getOrElse(Some(true): Rep[Option[Boolean]])
    }.filter { scene =>
      gridParams.month
        .map(datePart("month", scene.acquisitionDate) === _)
        .reduceLeftOption(_ || _)
        .getOrElse(true: Rep[Boolean])
    }.filter { scene =>
      gridParams.minDayOfMonth
        .map(datePart("day", scene.acquisitionDate) >= _)
        .reduceLeftOption(_ || _)
        .getOrElse(true: Rep[Boolean])
    }.filter { scene =>
      gridParams.maxDayOfMonth
        .map(datePart("day", scene.acquisitionDate) <= _)
        .reduceLeftOption(_ || _)
        .getOrElse(true: Rep[Boolean])
    }.filter { scene =>
      gridParams.datasource
        .map(scene.datasource === _)
        .reduceLeftOption(_ || _)
        .getOrElse(true: Rep[Boolean])
    }.filter { scene =>
      gridParams.ingested
        .map(scene.ingestLocation.isDefined === _)
        .reduceLeftOption(_ || _)
        .getOrElse(true: Rep[Boolean])
    }
    return filteredScenes
  }

  def filterByImageParams(imageParams: ImageQueryParameters): Scenes.TableQuery = {
    imageParams match {
      case ImageQueryParameters(None, None, None, None, _) => scenes
      case _ => scenes.filter { scene =>
        scene.id in Images.filterByImageParams(imageParams).map(_.scene)
      }
    }
  }

  def joinWithRelated: Scenes.JoinQuery = {
    for {
      (((scene, image), band), thumbnail) <-
      (scenes
         joinLeft Images on { case (s, i) => s.id === i.scene }
         joinLeft Bands on { case((x, i), b) => i.map(_.id) === b.imageId }
         joinLeft Thumbnails on { case(((s, i), b), t) => s.id === t.scene })
    } yield (scene, image, band, thumbnail)
  }
}

class ScenesJoinQuery[M, U, C[_]](sceneJoin: Scenes.JoinQuery) {
  /** Handle pagination with inner join on filtered scenes
   *
   * Pagination must be handled with an inner join here because the results
   * have duplicate scenes since there are many thumbnails + images per scene
   * potentially that have to be grouped server side.
   *
   * Filtering has to happen here because we need to filter the paginated results
   * and then do the inner join on those results
   */
  def page(combinedParams: CombinedSceneQueryParams, pageRequest: PageRequest): Scenes.JoinQuery = {
    val pagedScenes = Scenes
      .filterByOrganization(combinedParams.orgParams)
      .filterByUser(combinedParams.userParams)
      .filterByTimestamp(combinedParams.timestampParams)
      .filterBySceneParams(combinedParams.sceneParams)
      .filterByImageParams(combinedParams.imageQueryParameters)
      .sort(pageRequest.sort)
      .drop(pageRequest.offset * pageRequest.limit)
      .take(pageRequest.limit)
    val joinedResults = for {
      (pagedScene, join) <- pagedScenes join sceneJoin on (_.id === _._1.id)
    } yield (join)

    // Need to sort after the join because the join removes the sort order
    joinedResults.sort(pageRequest.sort)
  }
}
