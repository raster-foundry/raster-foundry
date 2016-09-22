package com.azavea.rf.bucket

import java.sql.Timestamp
import java.util.{Date, UUID}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

import slick.lifted._
import com.lonelyplanet.akka.http.extensions.PageRequest

import com.azavea.rf.AkkaSystem
import com.azavea.rf.datamodel.latest.schema.tables._
import com.azavea.rf.utils.{Database => DB, PaginatedResponse}
import com.azavea.rf.datamodel.driver.ExtendedPostgresDriver
import com.azavea.rf.scene.SceneFilters
import com.azavea.rf.scene.CombinedSceneQueryParams
import com.azavea.rf.utils.Slug
import com.azavea.rf.datamodel.enums.Visibility


/** Case class for bucket creation */
case class CreateBucket(
  organizationId: UUID,
  name: String,
  description: String,
  visibility: Visibility,
  tags: List[String]
) {
  def toBucket(userId: String): BucketsRow = {
    val now = new Timestamp((new java.util.Date()).getTime())
    BucketsRow(
      UUID.randomUUID, // primary key
      now, // createdAt
      now, // modifiedAt
      organizationId,
      userId, // createdBy
      userId, // modifiedBy
      name,
      Slug(name),
      description,
      visibility,
      Some(tags)
    )
  }
}


object BucketService extends AkkaSystem.LoggerExecutor {

  type BucketsQuery = Query[Buckets, Buckets#TableElementType, Seq]
  import BucketFilters._
  import SceneFilters._

  import ExtendedPostgresDriver.api._

  /** Add bucket to database
    *
    * @param bucket BucketsRow bucket to add to database
    */
  def insertBucket(bucket: BucketsRow)
    (implicit database: DB, ec: ExecutionContext): Future[Try[BucketsRow]] = {

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
    (implicit database: DB, ec: ExecutionContext): Future[PaginatedResponse[ScenesRow]] = {

    val bucketSceneQuery = for {
      bucketToScene <- ScenesToBuckets if bucketToScene.bucketId === bucketId
      scene <- Scenes if scene.id === bucketToScene.sceneId
    } yield (scene)

    val filteredScenes = bucketSceneQuery
      .filterBySceneParams(combinedParams.sceneParams)
      .filterByOrganization(combinedParams.orgParams)
      .filterByUser(combinedParams.userParams)
      .filterByTimestamp(combinedParams.timestampParams)

    val paginatedScenesQuery = database.db.run { filteredScenes.page(pageRequest).result }
    val totalScenesQuery = database.db.run { filteredScenes.length.result }
    for {
      totalScenes <- totalScenesQuery
      scenes <- paginatedScenesQuery
    } yield {
      val hasNext = (pageRequest.offset + 1) * pageRequest.limit < totalScenes
      val hasPrevious = pageRequest.offset > 0
      PaginatedResponse[ScenesRow](totalScenes, hasPrevious, hasNext,
        pageRequest.offset, pageRequest.limit, scenes)
    }
  }

  /** Get bucket given a bucketId
    *
    * @param bucketId UUID primary key of bucket to retrieve
    */
  def getBucket(bucketId: UUID)
    (implicit database: DB, ec: ExecutionContext): Future[Option[BucketsRow]] = {

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
    (implicit database: DB, ec: ExecutionContext): Future[PaginatedResponse[BucketsRow]] = {

    val buckets = Buckets.filterByOrganization(queryParams.orgParams)
      .filterByUser(queryParams.userParams)
      .filterByTimestamp(queryParams.timestampParams)

    val paginatedBuckets = database.db.run {
      val action = buckets.page(pageRequest).result
      log.debug(s"Query for buckets -- SQL ${action.statements.headOption}")
      action
    }

    val totalBucketsQuery = database.db.run { buckets.length.result }

    for {
      totalBuckets <- totalBucketsQuery
      buckets <- paginatedBuckets
    } yield {
      val hasNext = (pageRequest.offset + 1) * pageRequest.limit < totalBuckets
      val hasPrevious = pageRequest.offset > 0
      PaginatedResponse[BucketsRow](totalBuckets, hasPrevious, hasNext,
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
    * @param bucket BucketsRow bucket with updated values
    * @param bucketId UUID primary key of bucket to update
    * @param user UsersRow user updating bucket values
    */
  def updateBucket(bucket: BucketsRow, bucketId: UUID, user: UsersRow)
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
    (implicit database: DB, ec: ExecutionContext): Future[Try[ScenesToBucketsRow]] =  {

    val sceneToBucket = ScenesToBucketsRow(sceneId, bucketId)
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
