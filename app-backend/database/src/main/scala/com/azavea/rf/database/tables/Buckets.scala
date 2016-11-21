package com.azavea.rf.database.tables

import com.azavea.rf.database.fields._
import com.azavea.rf.database.sort._
import com.azavea.rf.datamodel._
import com.azavea.rf.database.query._
import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.database.ExtendedPostgresDriver.api._
import slick.model.ForeignKeyAction
import java.util.UUID
import java.util.Date
import java.sql.Timestamp
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import com.lonelyplanet.akka.http.extensions.PageRequest
import com.typesafe.scalalogging.LazyLogging

/** Table description of table buckets. Objects of this class serve as prototypes for rows in queries. */
class Buckets(_tableTag: Tag) extends Table[Bucket](_tableTag, "buckets")
                                      with BucketFields
                                      with OrganizationFkFields
                                      with UserFkFields
                                      with TimestampFields
                                      with VisibilityField
{
  def * = (id, createdAt, modifiedAt, organizationId, createdBy, modifiedBy, name, slugLabel, description, visibility, tags) <> (Bucket.tupled, Bucket.unapply)

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
  type TableQuery = Query[Buckets, Bucket, Seq]

  implicit val bucketsSorter: QuerySorter[Buckets] =
    new QuerySorter(
      new BucketFieldsSort(identity[Buckets]),
      new OrganizationFkSort(identity[Buckets]),
      new VisibilitySort(identity[Buckets]),
      new TimestampSort(identity[Buckets]))


  implicit class withBucketsTableQuery[M, U, C[_]](buckets: TableQuery) {
    def page(pageRequest: PageRequest): TableQuery = {
      val sorted = buckets.sort(pageRequest.sort)
      sorted.drop(pageRequest.offset * pageRequest.limit).take(pageRequest.limit)
    }
  }

  /** Add bucket to database
    *
    * @param bucket Bucket bucket to add to database
    */
  def insertBucket(bucket: Bucket)
                  (implicit database: DB): Future[Bucket] = {

    database.db.run {
      Buckets.forceInsert(bucket)
    } map { _ =>
      bucket
    }
  }

  /** Get scenes that belong to a bucket
    *
   * @param bucketId UUID bucket to request scenes for
    */
  def listBucketScenes(bucketId: UUID, pageRequest: PageRequest, combinedParams: CombinedSceneQueryParams)
                      (implicit database: DB): Future[PaginatedResponse[Scene.WithRelated]] = {

    val injectedParams = combinedParams.copy(
      sceneParams=combinedParams.sceneParams.copy(bucket=Some(bucketId))
    )

    Scenes.listScenes(pageRequest, injectedParams)
  }

  /** Get specific scenes from a bucket. Returns any scenes from the list of
    * specified ids that are associated to the given bucket.
    *
    * @param bucketId UUID primary key of the bucket to limit results to
    * @param sceneIds Seq[UUID] primary key of scenes to retrieve
    */
  def listSelectBucketScenes(bucketId: UUID, sceneIds: Seq[UUID])
                      (implicit database: DB): Future[Iterable[Scene.WithRelated]] = {

    val scenes = for {
      sceneToBucket <- ScenesToBuckets if sceneToBucket.bucketId === bucketId && sceneToBucket.sceneId.inSet(sceneIds)
      scene <- Scenes if scene.id === sceneToBucket.sceneId
    } yield scene

    database.db.run {
      scenes.joinWithRelated.result
    } map Scene.WithRelated.fromRecords
  }

  /** Get bucket given a bucketId
    *
    * @param bucketId UUID primary key of bucket to retrieve
    */
  def getBucket(bucketId: UUID)
               (implicit database: DB): Future[Option[Bucket]] = {

    database.db.run {
      Buckets.filter(_.id === bucketId).result.headOption
    }
  }

  /** List buckets after applying filters and sorting
    *
    * @param pageRequest PageRequest pagination parameters
    * @param queryParams BucketQueryParams query parameters relevant for buckets
    */
  def listBuckets(pageRequest: PageRequest, queryParams: BucketQueryParameters)
                 (implicit database: DB): Future[PaginatedResponse[Bucket]] = {

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
  def deleteBucket(bucketId: UUID)(implicit database: DB): Future[Int] = {

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
                  (implicit database: DB): Future[Int] = {

    val updateTime = new Timestamp((new Date).getTime)

    val updateBucketQuery = for {
      updateBucket <- Buckets.filter(_.id === bucketId)
    } yield (
      updateBucket.modifiedAt, updateBucket.modifiedBy, updateBucket.name, updateBucket.description,
      updateBucket.visibility, updateBucket.tags
    )
    database.db.run {
      updateBucketQuery.update((
        updateTime, user.id, bucket.name, bucket.description, bucket.visibility, bucket.tags
      ))
    } map {
      case 1 => 1
      case c => throw new IllegalStateException(s"Error updating bucket: update result expected to be 1, was $c")
    }
  }

  /** Adds a list of scenes to a bucket
    *
    * @param sceneIds Seq[UUID] list of primary keys of scenes to add to bucket
    * @param bucketId UUID primary key of back to add scenes to
    */
  def addScenesToBucket(sceneIds: Seq[UUID], bucketId: UUID)
                       (implicit database: DB): Future[Iterable[Scene.WithRelated]] = {

    val sceneBucketJoinQuery = for {
      s <- ScenesToBuckets if s.sceneId.inSet(sceneIds) && s.bucketId === bucketId
    } yield s.sceneId

    database.db.run {
      sceneBucketJoinQuery.result
    } map { alreadyAdded =>
      val newScenes = sceneIds.filterNot(alreadyAdded.toSet)
      val newScenesToBuckets = newScenes.map { sceneId =>
        SceneToBucket(sceneId, bucketId)
      }

      database.db.run {
        ScenesToBuckets.forceInsertAll(newScenesToBuckets)
      }
    }

    listSelectBucketScenes(bucketId, sceneIds)
  }

  /** Removes scenes from bucket
    *
    * @param sceneIds Seq[UUID] primary key of scenes to remove from bucket
    * @param bucketId UUID primary key of bucket these scenes will be removed from
    */
  def deleteScenesFromBucket(sceneIds: Seq[UUID], bucketId: UUID)
                            (implicit database: DB): Future[Int] = {

    val sceneBucketJoinQuery = for {
      s <- ScenesToBuckets if s.sceneId.inSet(sceneIds) && s.bucketId === bucketId
    } yield s

    database.db.run {
      sceneBucketJoinQuery.delete
    }
  }

  /** Removes all scenes from bucket, then adds all specified scenes to it
    *
    * @param sceneIds Seq[UUID] primary key of scenes to add to bucket
    * @param bucketId UUID primary key of bucket to remove all scenes from and add specified scenes to
    * @return Scenes that were added
    */
  def replaceScenesInBucket(sceneIds: Seq[UUID], bucketId: UUID)
                           (implicit database: DB): Future[Iterable[Scene.WithRelated]] = {

    val scenesToBuckets = sceneIds.map { sceneId =>
      SceneToBucket(sceneId, bucketId)
    }

    val actions = DBIO.seq(
      ScenesToBuckets.filter(_.bucketId === bucketId).delete,
      ScenesToBuckets.forceInsertAll(scenesToBuckets)
    )

    database.db.run {
      actions.transactionally
    }

    listSelectBucketScenes(bucketId, sceneIds)
  }
}
