package com.azavea.rf.database.tables

import com.azavea.rf.datamodel._
import com.azavea.rf.database.query._
import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.database.ExtendedPostgresDriver.api._
import slick.model.ForeignKeyAction
import java.util.UUID
import java.util.Date
import java.sql.Timestamp
import scala.concurrent.{Future, ExecutionContext}
import scala.util.{Try, Success, Failure}
import com.lonelyplanet.akka.http.extensions.{PageRequest, Order}
import com.typesafe.scalalogging.LazyLogging

/** Table description of table buckets. Objects of this class serve as prototypes for rows in queries. */
class Buckets(_tableTag: Tag) extends Table[Bucket](_tableTag, "buckets")
                                      with HasOrganizationFK
                                      with HasUserFK
                                      with HasTimestamp
{
  def * = (id, createdAt, modifiedAt, organizationId, createdBy, modifiedBy, name, slugLabel, description, visibility, tags) <> (Bucket.tupled, Bucket.unapply)
  /** Maps whole row to an option. Useful for outer joins. */
  def ? = (Rep.Some(id), Rep.Some(createdAt), Rep.Some(modifiedAt), Rep.Some(organizationId), Rep.Some(createdBy), Rep.Some(modifiedBy), Rep.Some(name), Rep.Some(slugLabel), Rep.Some(description), Rep.Some(visibility), tags).shaped.<>({r=>import r._; _1.map(_=> Bucket.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get, _7.get, _8.get, _9.get, _10.get, _11)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

  val id: Rep[java.util.UUID] = column[java.util.UUID]("id", O.PrimaryKey)
  val createdAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created_at")
  val modifiedAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("modified_at")
  val organizationId: Rep[java.util.UUID] = column[java.util.UUID]("organization_id")
  val createdBy: Rep[String] = column[String]("created_by", O.Length(255,varying=true))
  val modifiedBy: Rep[String] = column[String]("modified_by", O.Length(255,varying=true))
  val name: Rep[String] = column[String]("name")
  val slugLabel: Rep[String] = column[String]("slug_label", O.Length(255,varying=true))
  val description: Rep[String] = column[String]("description")
  val visibility: Rep[Visibility] = column[Visibility]("visibility")
  val tags: Rep[List[String]] = column[List[String]]("tags", O.Length(2147483647,varying=false), O.Default(List.empty))

  lazy val organizationsFk = foreignKey("buckets_organization_id_fkey", organizationId, Organizations)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val createdByUserFK = foreignKey("buckets_created_by_fkey", createdBy, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val modifiedByUserFK = foreignKey("buckets_modified_by_fkey", modifiedBy, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
}

object Buckets extends TableQuery(tag => new Buckets(tag)) with LazyLogging {
  type TableQuery = Query[Buckets, Buckets#TableElementType, Seq]

  implicit class withBucketsTableQuery[M, U, C[_]](buckets: Buckets.TableQuery) extends
      BucketsTableQuery[M, U, C](buckets)

  /** Add bucket to database
    *
    * @param bucket Bucket bucket to add to database
    */
  def insertBucket(bucket: Bucket)
    (implicit database: DB, ec: ExecutionContext): Future[Try[Bucket]] = {

    database.db.run {
      Buckets.forceInsert(bucket).asTry
    } map {
      case Success(_) => Success(bucket)
      case Failure(e) => Failure(e)
    }
  }

  /** Get scenes that belong to a bucket
    *
    * @param bucketId UUID bucket to request scenes for
    */
  def getBucketScenes(bucketId: UUID, pageRequest: PageRequest, combinedParams: CombinedSceneQueryParams)
    (implicit database: DB, ec: ExecutionContext): Future[PaginatedResponse[Scene.WithRelated]] = {


    val bucketSceneQuery = for {
      bucketToScene <- ScenesToBuckets if bucketToScene.bucketId === bucketId
      scene <- Scenes if scene.id === bucketToScene.sceneId
    } yield scene

    val pagedScenes = bucketSceneQuery
      .joinWithRelated
      .page(combinedParams, pageRequest)

    val scenesQueryResult = database.db.run {
      val action = pagedScenes.result
      logger.debug(s"Total Query for scenes -- SQL: ${action.statements.headOption}")
      action
    } map(Scenes.createScenesWithRelated)

    val totalScenesQueryResult = database.db.run {
      val action = bucketSceneQuery
        .filterBySceneParams(combinedParams.sceneParams)
        .filterByOrganization(combinedParams.orgParams)
        .filterByUser(combinedParams.userParams)
        .filterByTimestamp(combinedParams.timestampParams).length.result
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

  /** Get bucket given a bucketId
    *
    * @param bucketId UUID primary key of bucket to retrieve
    */
  def getBucket(bucketId: UUID)
    (implicit database: DB, ec: ExecutionContext): Future[Option[Bucket]] = {

    database.db.run {
      Buckets.filter(_.id === bucketId).result.headOption
    }
  }

  /** List buckets after applying filters and sorting
    *
    * @param pageRequest PageRequest pagination parameters
    * @param queryParams BucketQueryParams query parameters relevant for buckets
    */
  def getBuckets(pageRequest: PageRequest, queryParams: BucketQueryParameters)
    (implicit database: DB, ec: ExecutionContext): Future[PaginatedResponse[Bucket]] = {

    val buckets = Buckets.filterByOrganization(queryParams.orgParams)
      .filterByUser(queryParams.userParams)
      .filterByTimestamp(queryParams.timestampParams)

    val paginatedBuckets = database.db.run {
      val action = buckets.page(pageRequest).result
      logger.debug(s"Query for buckets -- SQL ${action.statements.headOption}")
      action
    }

    val totalBucketsQuery = database.db.run { buckets.length.result }

    for {
      totalBuckets <- totalBucketsQuery
      buckets <- paginatedBuckets
    } yield {
      val hasNext = (pageRequest.offset + 1) * pageRequest.limit < totalBuckets
      val hasPrevious = pageRequest.offset > 0
      PaginatedResponse[Bucket](totalBuckets, hasPrevious, hasNext,
        pageRequest.offset, pageRequest.limit, buckets)
    }
  }

  /** Delete a given bucket from the database
    *
    * @param bucketId UUID primary key of bucket to delete
    */
  def deleteBucket(bucketId: UUID)(implicit database: DB, ec: ExecutionContext): Future[Int] = {

    database.db.run {
      Buckets.filter(_.id === bucketId).delete
    }
  }

  /** Update a given bucket
    *
    * Currently allows updating the following attributes of a bucket:
    *  - name
    *  - description
    *  - visibility
    *  - tags
    *
    * Separate functions exist to remove/add scenes to a bucket
    *
    * @param bucket Bucket bucket with updated values
    * @param bucketId UUID primary key of bucket to update
    * @param user User user updating bucket values
    */
  def updateBucket(bucket: Bucket, bucketId: UUID, user: User)
    (implicit database: DB, ec: ExecutionContext): Future[Try[Int]] = {

    val updateTime = new Timestamp((new Date()).getTime())

    val updateBucketQuery = for {
      updateBucket <- Buckets.filter(_.id === bucketId)
    } yield (
      updateBucket.modifiedAt, updateBucket.modifiedBy, updateBucket.name, updateBucket.description,
      updateBucket.visibility, updateBucket.tags
    )
    database.db.run {
      updateBucketQuery.update((
        updateTime, user.id, bucket.name, bucket.description, bucket.visibility, bucket.tags
      )).asTry
    } map {
      case Success(result) => {
        result match {
          case 1 => Success(1)
          case _ => Failure(new Exception("Error while updating bucket"))
        }
      }
      case Failure(e) => Failure(e)
    }
  }

  /** Adds a scene to a bucket
    *
    * @param sceneId UUID primary key of scene to add to bucket
    * @param bucketId UUID primary key of bucket to add scene to
    */
  def addSceneToBucket(sceneId: UUID, bucketId: UUID)
    (implicit database: DB, ec: ExecutionContext): Future[Try[SceneToBucket]] =  {

    val sceneToBucket = SceneToBucket(sceneId, bucketId)
    database.db.run {
      ScenesToBuckets.forceInsert(sceneToBucket).asTry
    } map {
      case Success(_) => Success(sceneToBucket)
      case Failure(e) => Failure(e)
    }
  }

  /** Removes scene from bucket
    *
    * @param sceneId UUID primary key of scene to remove from bucket
    * @param bucketId UUID primary key of bucket that scene will be removed from
    */
  def deleteSceneFromBucket(sceneId: UUID, bucketId: UUID)
    (implicit database: DB, ec: ExecutionContext): Future[Int] = {

    val sceneBucketJoinQuery = for {
      s <- ScenesToBuckets if s.sceneId === sceneId && s.bucketId === bucketId
    } yield (s)

    database.db.run {
      sceneBucketJoinQuery.delete
    }
  }
}


class BucketsTableQuery[M, U, C[_]](buckets: Buckets.TableQuery) {
  def sort(sortMap: Map[String, Order]): Buckets.TableQuery = {
    def applySort(query: Buckets.TableQuery, sortMap: Map[String, Order]): Buckets.TableQuery = {
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

        case Some(("name", Order.Asc)) => applySort(query.sortBy(_.name.asc),
                                                    sortMap.tail)
        case Some(("name", Order.Desc)) => applySort(query.sortBy(_.name.desc),
                                                     sortMap.tail)

        case Some(("visibility", Order.Asc)) => applySort(query.sortBy(_.visibility.asc),
                                                          sortMap.tail)
        case Some(("visibility", Order.Desc)) => applySort(query.sortBy(_.visibility.desc),
                                                           sortMap.tail)

        case Some(_) => applySort(query, sortMap.tail)
        case _ => query
      }
    }
    applySort(buckets, sortMap)
  }

  def page(pageRequest: PageRequest): Buckets.TableQuery = {
    val sorted = buckets.sort(pageRequest.sort)
    sorted.drop(pageRequest.offset * pageRequest.limit).take(pageRequest.limit)
  }
}
