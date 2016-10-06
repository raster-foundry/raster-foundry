package com.azavea.rf.database.tables

import com.azavea.rf.database.query._
import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.datamodel._
import java.util.UUID
import java.sql.Timestamp
import geotrellis.slick.Projected
import geotrellis.vector.{Point, Polygon, Geometry}
import scala.concurrent.{Future, ExecutionContext}
import scala.util.{Try, Success, Failure}
import com.typesafe.scalalogging.LazyLogging
import com.lonelyplanet.akka.http.extensions.{PageRequest, Order}

/** Table description of table scenes. Objects of this class serve as prototypes for rows in queries. */
class Scenes(_tableTag: Tag) extends Table[Scene](_tableTag, "scenes")
                                     with HasOrganizationFK
                                     with HasUserFK
                                     with HasTimestamp
{
  def * = (id, createdAt, modifiedAt, organizationId, createdBy, modifiedBy, ingestSizeBytes, visibility, resolutionMeters, tags, datasource, sceneMetadata, cloudCover, acquisitionDate, thumbnailStatus, boundaryStatus, status, sunAzimuth, sunElevation, name, footprint) <> (Scene.tupled, Scene.unapply)
  /** Maps whole row to an option. Useful for outer joins. */
  def ? = (Rep.Some(id), Rep.Some(createdAt), Rep.Some(modifiedAt), Rep.Some(organizationId), Rep.Some(createdBy), Rep.Some(modifiedBy), Rep.Some(ingestSizeBytes), Rep.Some(visibility), Rep.Some(resolutionMeters), Rep.Some(tags), Rep.Some(datasource), Rep.Some(sceneMetadata), cloudCover, acquisitionDate, Rep.Some(thumbnailStatus), Rep.Some(boundaryStatus), Rep.Some(status), sunAzimuth, sunElevation, Rep.Some(name), footprint).shaped.<>({r=>import r._; _1.map(_=> Scene.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get, _7.get, _8.get, _9.get, _10.get, _11.get, _12.get, _13, _14, _15.get, _16.get, _17.get, _18, _19, _20.get, _21)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

  val id: Rep[java.util.UUID] = column[java.util.UUID]("id", O.PrimaryKey)
  val createdAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created_at")
  val modifiedAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("modified_at")
  val organizationId: Rep[java.util.UUID] = column[java.util.UUID]("organization_id")
  val createdBy: Rep[String] = column[String]("created_by", O.Length(255,varying=true))
  val modifiedBy: Rep[String] = column[String]("modified_by", O.Length(255,varying=true))
  val ingestSizeBytes: Rep[Int] = column[Int]("ingest_size_bytes")
  val visibility: Rep[Visibility] = column[Visibility]("visibility")
  val resolutionMeters: Rep[Float] = column[Float]("resolution_meters")
  val tags: Rep[List[String]] = column[List[String]]("tags", O.Length(2147483647,varying=false))
  val datasource: Rep[String] = column[String]("datasource", O.Length(255,varying=true))
  val sceneMetadata: Rep[Map[String, Any]] = column[Map[String, Any]]("scene_metadata", O.Length(2147483647,varying=false))
  val cloudCover: Rep[Option[Float]] = column[Option[Float]]("cloud_cover", O.Default(None))
  val acquisitionDate: Rep[Option[java.sql.Timestamp]] = column[Option[java.sql.Timestamp]]("acquisition_date", O.Default(None))
  val thumbnailStatus: Rep[JobStatus] = column[JobStatus]("thumbnail_status")
  val boundaryStatus: Rep[JobStatus] = column[JobStatus]("boundary_status")
  val status: Rep[JobStatus] = column[JobStatus]("status")
  val sunAzimuth: Rep[Option[Float]] = column[Option[Float]]("sun_azimuth", O.Default(None))
  val sunElevation: Rep[Option[Float]] = column[Option[Float]]("sun_elevation", O.Default(None))
  val name: Rep[String] = column[String]("name", O.Length(255,varying=true))
  val footprint: Rep[Option[Projected[Geometry]]] = column[Option[Projected[Geometry]]]("footprint", O.Length(2147483647,varying=false), O.Default(None))

  /** Foreign key referencing Organizations (database name scenes_organization_id_fkey) */
  lazy val organizationsFk = foreignKey("scenes_organization_id_fkey", organizationId, Organizations)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val createdByUserFK = foreignKey("scenes_created_by_fkey", createdBy, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val modifiedByUserFK = foreignKey("scenes_modified_by_fkey", modifiedBy, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)

  /** Uniqueness Index over (name,organizationId,datasource) (database name scene_name_org_datasource) */
  val index1 = index("scene_name_org_datasource", (name, organizationId, datasource), unique=true)
}

/** Collection-like TableQuery object for table Scenes */
object Scenes extends TableQuery(tag => new Scenes(tag)) with LazyLogging {

  type TableQuery = Query[Scenes, Scenes#TableElementType, Seq]
  type JoinQuery = Query[(Scenes, Rep[Option[Images]], Rep[Option[Thumbnails]]),(Scene, Option[Image], Option[Thumbnail]), Seq]

  implicit class withScenesTableQuery[M, U, C[_]](scenes: Scenes.TableQuery) extends
      ScenesTableQuery[M, U, C](scenes)

  implicit class withScenesJoinQuery[M, U, C[_]](scenes: Scenes.JoinQuery) extends
      ScenesJoinQuery[M, U, C](scenes)

  val datePart = SimpleFunction.binary[String, Option[Timestamp], Int]("date_part")

  /** Insert a scene into the database
    *
    * @param scene CreateScene scene to create
    * @param user User user creating scene
    *
    * This implementation allows a user to post a scene with thumbnails, footprint, and
    * images which are all created in a single transaction
    */
  def insertScene(ss: CreateScene, user: User)
    (implicit database: DB, ec: ExecutionContext): Future[Try[SceneWithRelated]] = {

    val scene = ss.toScene(user.id)
    val scenesRowInsert = Scenes.forceInsert(scene)

    val thumbnails = ss.thumbnails.map(_.toThumbnail(user.id, scene))
    val thumbnailsInsert = DBIO.seq(thumbnails.map(Thumbnails.forceInsert): _*)

    val images = ss.images.map(_.toImage(user.id, scene))
    val imagesInsert = DBIO.seq(images.map(Images.forceInsert): _*)

    val sceneInsert = (for {
      _ <- scenesRowInsert
      _ <- thumbnailsInsert
      _ <- imagesInsert
    } yield ()).transactionally

    database.db.run {
      sceneInsert.asTry
    } map {
      case Success(_) => Success(
        SceneWithRelated.fromComponents(scene, images, thumbnails)
      )
      case Failure(e) => {
        logger.error(e.toString)
        Failure(e)
      }
    }
  }

  /** Helper function to create Iterable[SceneWithRelated] from join
    *
    * It is necessary to map over the distinct scenes because that is the only way to
    * ensure that the sort order of the query result remains ordered after grouping
    *
    * @param joinResult result of join query to return scene with related
    * information
    */
  def createScenesWithRelated(joinResult: Seq[(Scene, Option[Image], Option[Thumbnail])]): Iterable[SceneWithRelated] = {

    val distinctScenes = joinResult.map(_._1).distinct
    val grouped = joinResult.groupBy(_._1)
    distinctScenes.map{scene =>
      // This should be relatively safe since scene is the key grouped by
      val (seqImages, seqThumbnails) = grouped(scene)
        .map{ case (sr, im, th) => (im, th)}
        .unzip
      SceneWithRelated.fromComponents(
        scene, seqImages.flatten, seqThumbnails.flatten.distinct
      )
    }
  }

  /** Retrieve a single scene from the database
    *
    * @param sceneId java.util.UUID ID of scene to query with
    */
  def getScene(sceneId: java.util.UUID)
    (implicit database: DB, ec: ExecutionContext): Future[Option[SceneWithRelated]] = {
    val sceneJoinQuery = Scenes
      .filter(_.id === sceneId)
      .joinWithRelated

    database.db.run {
      val action = sceneJoinQuery.result
      logger.debug(s"Total Query for scenes -- SQL: ${action.statements.headOption}")
      action
    } map { join =>
      val scenesWithRelated = createScenesWithRelated(join)
      scenesWithRelated.headOption
    }
  }

  /** Get scenes given a page request and query parameters */
  def getScenes(pageRequest: PageRequest, combinedParams: CombinedSceneQueryParams)
    (implicit database: DB, ec: ExecutionContext): Future[PaginatedResponse[SceneWithRelated]] = {

    val pagedScenes = Scenes
      .joinWithRelated
      .page(combinedParams, pageRequest)

    val scenesQueryResult = database.db.run {
      val action = pagedScenes.result
      logger.debug(s"Paginated Query for scenes -- SQL: ${action.statements.headOption}")
      action
    } map(join => createScenesWithRelated(join))

    val totalScenesQueryResult = database.db.run {
      val action = Scenes
        .filterByOrganization(combinedParams.orgParams)
        .filterByUser(combinedParams.userParams)
        .filterByTimestamp(combinedParams.timestampParams)
        .filterBySceneParams(combinedParams.sceneParams)
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


  /** Delete a scene from the database
    *
    * @param sceneId java.util.UUID ID of scene to delete
    */
  def deleteScene(sceneId: UUID)(implicit database: DB, ec: ExecutionContext): Future[Int] = {
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
    (implicit database: DB, ec: ExecutionContext): Future[Try[Int]] = {

    val updateTime = new Timestamp((new java.util.Date()).getTime())

    val updateSceneQuery = for {
      updateScene <- Scenes.filter(_.id === sceneId)
    } yield (
      updateScene.modifiedAt, updateScene.modifiedBy, updateScene.ingestSizeBytes,
      updateScene.resolutionMeters, updateScene.datasource, updateScene.cloudCover,
      updateScene.acquisitionDate, updateScene.tags, updateScene.sceneMetadata,
      updateScene.thumbnailStatus, updateScene.boundaryStatus, updateScene.status, updateScene.name, updateScene.footprint
    )
    database.db.run {
      updateSceneQuery.update((
        updateTime, user.id, scene.ingestSizeBytes, scene.resolutionMeters,
        scene.datasource, scene.cloudCover, scene.acquisitionDate, scene.tags, scene.sceneMetadata,
        scene.thumbnailStatus, scene.boundaryStatus, scene.status, scene.name, scene.footprint
      )).asTry
    } map {
      case Success(result) => {
        result match {
          case 1 => Success(1)
          case _ => Failure(new Exception("Error while updating scene"))
        }
      }
      case Failure(e) => Failure(e)
    }
  }
}


class ScenesTableQuery[M, U, C[_]](scenes: Scenes.TableQuery) {
  import Scenes.datePart

  def filterBySceneParams(sceneParams: SceneQueryParameters): Scenes.TableQuery = {
    scenes.filter{ scene =>
      val sceneFilterConditions = List(
        sceneParams.maxAcquisitionDatetime.map(scene.acquisitionDate < _),
        sceneParams.minAcquisitionDatetime.map(scene.acquisitionDate > _),
        sceneParams.maxCloudCover.map(scene.cloudCover < _),
        sceneParams.minCloudCover.map(scene.cloudCover > _),
        sceneParams.minSunAzimuth.map(scene.sunAzimuth > _),
        sceneParams.maxSunAzimuth.map(scene.sunAzimuth < _),
        sceneParams.minSunElevation.map(scene.sunElevation > _),
        sceneParams.maxSunElevation.map(scene.sunElevation < _),
        sceneParams.bbox.map { bboxString =>
          val (xmin, ymin, xmax, ymax) = bboxString.split(",") match {
            case Array(xmin, ymin, xmax, ymax) =>
              (xmin.toDouble, ymin.toDouble, xmax.toDouble, ymax.toDouble)
            case _ => throw new IllegalArgumentException(
              "Four comma separated coordinates must be given"
            )

          }
          val p1 = Point(xmin, ymin)
          val p2 = Point(xmax, ymin)
          val p3 = Point(xmax, ymax)
          val p4 = Point(xmin, ymax)
          val bbox = Projected(Polygon(Seq(p1,p2,p3,p4,p1)), 3857)
          scene.footprint.intersects(bbox)
        },
        sceneParams.point.map { pointString =>
          val pt = pointString.split(",") match {
            case Array(x, y) => Projected(Point(x.toDouble, y.toDouble), 3857)
            case _ => throw new IllegalArgumentException(
              "Both coordinate parameters (x, y) must be specified"
            )
          }
          scene.footprint.intersects(pt)
        }
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
      sceneParams.datasource
        .map(scene.datasource === _)
        .reduceLeftOption(_ || _)
        .getOrElse(true: Rep[Boolean])
    }
  }

  /** Return a join query for scenes
   *
   * @sceneQuery ScenesQuery base scenes query
   */
  def joinWithRelated = {
    for {
      ((scene, image), thumbnail) <-
      (scenes
         joinLeft Images on (_.id === _.scene)
         joinLeft Thumbnails on (_._1.id === _.scene))
    } yield( scene, image, thumbnail )
  }

  def sort(sortMap: Map[String, Order]): Scenes.TableQuery = {
    def applySort(query: Scenes.TableQuery, sortMap: Map[String, Order]): Scenes.TableQuery = {
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

        case Some(("datasource", Order.Asc)) => applySort(query.sortBy(_.datasource.asc),
                                                          sortMap.tail)
        case Some(("datasource", Order.Desc)) => applySort(query.sortBy(_.datasource.desc),
                                                           sortMap.tail)

        case Some(("month", Order.Asc)) => applySort(query.sortBy { join =>
                                                       datePart("month", join.acquisitionDate).asc
                                                     }, sortMap.tail)
        case Some(("month", Order.Desc)) => applySort(query.sortBy { join =>
                                                        datePart("month", join.acquisitionDate).desc
                                                      }, sortMap.tail)

        case Some(("acquisitionDatetime", Order.Asc)) => applySort(
          query.sortBy(_.acquisitionDate.asc), sortMap.tail)
        case Some(("acquisitionDatetime", Order.Desc)) => applySort(
          query.sortBy(_.acquisitionDate.desc), sortMap.tail)

        case Some(("sunAzimuth", Order.Asc)) => applySort(query.sortBy(_.sunAzimuth.asc),
                                                          sortMap.tail)
        case Some(("sunAzimuth", Order.Desc)) => applySort(query.sortBy(_.sunAzimuth.desc),
                                                           sortMap.tail)

        case Some(("sunElevation", Order.Asc)) => applySort(query.sortBy(_.sunElevation.asc),
                                                            sortMap.tail)
        case Some(("sunElevation", Order.Desc)) => applySort(query.sortBy(_.sunElevation.desc),
                                                             sortMap.tail)

        case Some(("cloudCover", Order.Asc)) => applySort(query.sortBy(_.cloudCover.asc),
                                                          sortMap.tail)
        case Some(("cloudCover", Order.Desc)) => applySort(query.sortBy(_.cloudCover.desc),
                                                           sortMap.tail)

        case Some(_) => applySort(query, sortMap.tail)
        case _ => query
      }
    }
    applySort(scenes, sortMap)
  }
}

class ScenesJoinQuery[M, U, C[_]](sceneJoin: Scenes.JoinQuery) {
  import Scenes.datePart

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
      .sort(pageRequest.sort)
      .drop(pageRequest.offset * pageRequest.limit)
      .take(pageRequest.limit)
    val joinedResults = for {
      (pagedScene, join) <- pagedScenes join sceneJoin on (_.id === _._1.id)
    } yield (join)

    // Need to sort after the join because the join removes the sort order
    joinedResults.sort(pageRequest.sort)
  }

  def sort(sortMap: Map[String, Order]): Scenes.JoinQuery = {
    def applySort(query: Scenes.JoinQuery, sortMap: Map[String, Order]): Scenes.JoinQuery = {
      sortMap.headOption match {
        case Some(("createdAt", Order.Asc)) => applySort(query.sortBy(_._1.createdAt.asc),
                                                         sortMap.tail)
        case Some(("createdAt", Order.Desc)) => applySort(query.sortBy(_._1.createdAt.desc),
                                                          sortMap.tail)

        case Some(("modifiedAt", Order.Asc)) => applySort(query.sortBy(_._1.modifiedAt.asc),
                                                          sortMap.tail)
        case Some(("modifiedAt", Order.Desc)) => applySort(query.sortBy(_._1.modifiedAt.desc),
                                                           sortMap.tail)

        case Some(("organization", Order.Asc)) => applySort(query.sortBy(_._1.organizationId.asc),
                                                            sortMap.tail)
        case Some(("organization", Order.Desc)) => applySort(query.sortBy(_._1.organizationId.desc),
                                                             sortMap.tail)

        case Some(("datasource", Order.Asc)) => applySort(query.sortBy(_._1.datasource.asc),
                                                          sortMap.tail)
        case Some(("datasource", Order.Desc)) => applySort(query.sortBy(_._1.datasource.desc),
                                                           sortMap.tail)

        case Some(("month", Order.Asc)) => applySort(query.sortBy { join =>
                                                       datePart("month", join._1.acquisitionDate).asc
                                                     }, sortMap.tail)
        case Some(("month", Order.Desc)) => applySort(query.sortBy { join =>
                                                        datePart("month", join._1.acquisitionDate).desc
                                                      }, sortMap.tail)

        case Some(("acquisitionDatetime", Order.Asc)) => applySort(
          query.sortBy(_._1.acquisitionDate.asc), sortMap.tail)
        case Some(("acquisitionDatetime", Order.Desc)) => applySort(
          query.sortBy(_._1.acquisitionDate.desc), sortMap.tail)

        case Some(("sunAzimuth", Order.Asc)) => applySort(query.sortBy(_._1.sunAzimuth.asc),
                                                          sortMap.tail)
        case Some(("sunAzimuth", Order.Desc)) => applySort(query.sortBy(_._1.sunAzimuth.desc),
                                                           sortMap.tail)

        case Some(("sunElevation", Order.Asc)) => applySort(query.sortBy(_._1.sunElevation.asc),
                                                            sortMap.tail)
        case Some(("sunElevation", Order.Desc)) => applySort(query.sortBy(_._1.sunElevation.desc),
                                                             sortMap.tail)

        case Some(("cloudCover", Order.Asc)) => applySort(query.sortBy(_._1.cloudCover.asc),
                                                          sortMap.tail)
        case Some(("cloudCover", Order.Desc)) => applySort(query.sortBy(_._1.cloudCover.desc),
                                                           sortMap.tail)

        case Some(_) => applySort(sceneJoin, sortMap.tail)
        case _ => query
      }
    }
    applySort(sceneJoin, sortMap)
  }
}
