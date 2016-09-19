package com.azavea.rf.bucket

import java.sql.Timestamp
import java.util.{Date, UUID}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

import com.azavea.rf.datamodel.latest.schema.tables._
import com.azavea.rf.utils.Database


trait Bucket {

  implicit val database:Database
  implicit val ec:ExecutionContext

  import database.driver.api._

  /** Add bucket to database
    *
    * @param bucket BucketsRow bucket to add to database
    */
  def insertBucket(bucket: BucketsRow): Future[Try[BucketsRow]] = {
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
  def getBucketScenes(bucketId: UUID): Future[Seq[ScenesRow]] = {
    val bucketSceneQuery = for {
      bucketToScene <- ScenesToBuckets if bucketToScene.bucketId === bucketId
      scene <- Scenes if scene.id === bucketToScene.sceneId
    } yield (scene)
    database.db.run {
      bucketSceneQuery.result
    }
  }

  /** Get bucket given a bucketId
    *
    * @param bucketId UUID primary key of bucket to retrieve
    */
  def getBucket(bucketId: UUID): Future[Option[BucketsRow]] = {
    database.db.run {
      Buckets.filter(_.id === bucketId).result.headOption
    }
  }

  /** Delete a given bucket from the database
    *
    * @param bucketId UUID primary key of bucket to delete
    */
  def deleteBucket(bucketId: UUID): Future[Try[Int]] = {
    database.db.run {
      Buckets.filter(_.id === bucketId).delete.asTry
    } map {
      case Success(result) => {
        result match {
          case 1 => Success(1)
          case _ => Failure(new Exception("Error while deleting bucket"))
        }
      }
      case Failure(e) => Failure(e)
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
  def updateBucket(bucket: BucketsRow, bucketId: UUID, user: UsersRow): Future[Try[Int]] = {
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
  def addSceneToBucket(sceneId: UUID, bucketId: UUID): Future[Try[ScenesToBucketsRow]] =  {
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
  def deleteSceneFromBucket(sceneId: UUID, bucketId: UUID): Future[Try[Int]] = {
    val sceneBucketJoinQuery = for {
      s <- ScenesToBuckets if s.sceneId === sceneId && s.bucketId === bucketId
    } yield (s)

    database.db.run {
      sceneBucketJoinQuery.delete.asTry
    } map {
      case Success(result) => {
        result match {
          case 1 => Success(1)
          case _ => Failure(new Exception(
            s"Error while removing scene ($sceneId) from  bucket ($bucketId)")
          )
        }
      }
      case Failure(e) => Failure(e)
    }
  }
}
