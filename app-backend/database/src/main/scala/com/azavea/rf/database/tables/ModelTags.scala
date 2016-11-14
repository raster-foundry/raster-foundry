package com.azavea.rf.database.tables

import java.sql.Timestamp
import java.util.UUID

import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.database.fields.{OrganizationFkFields, TimestampFields, UserFkFields}
import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.datamodel._
import com.lonelyplanet.akka.http.extensions.PageRequest
import com.typesafe.scalalogging.LazyLogging
import slick.model.ForeignKeyAction

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/** Table that represents tags for models in model lab
  *
  * These are user generated tags for models to help users
  * track and search for them. For instance, users may decide to
  * create tags for coursework, projects, etc.
  */
class ModelTags(_tableTag: Tag)
    extends Table[ModelTag](_tableTag, "model_tags")
    with LazyLogging
    with OrganizationFkFields
    with UserFkFields
    with TimestampFields {

  def * =
    (id, createdAt, modifiedAt, organizationId, createdBy, modifiedBy, tag) <> (ModelTag.tupled, ModelTag.unapply)

  val id: Rep[UUID] = column[UUID]("id", O.PrimaryKey)
  val createdAt: Rep[Timestamp] = column[Timestamp]("created_at")
  val createdBy: Rep[String] = column[String]("created_by", O.Length(255, varying = true))
  val modifiedAt: Rep[Timestamp] = column[Timestamp]("modified_at")
  val modifiedBy: Rep[String] = column[String]("modified_by", O.Length(255, varying = true))
  val organizationId: Rep[UUID] = column[UUID]("organization_id")
  val tag: Rep[String] = column[String]("tag")

  lazy val organizationsFk =
    foreignKey("model_tags_organization_id_fkey", organizationId, Organizations)(
      r => r.id,
      onUpdate = ForeignKeyAction.NoAction,
      onDelete = ForeignKeyAction.NoAction)
  lazy val createdByUserFK = foreignKey("model_tags_created_by_fkey", createdBy, Users)(
    r => r.id,
    onUpdate = ForeignKeyAction.NoAction,
    onDelete = ForeignKeyAction.NoAction)
  lazy val modifiedByUserFK = foreignKey("model_tags_modified_by_fkey", modifiedBy, Users)(
    r => r.id,
    onUpdate = ForeignKeyAction.NoAction,
    onDelete = ForeignKeyAction.NoAction)

}

object ModelTags extends TableQuery(tag => new ModelTags(tag)) with LazyLogging {

  type TableQuery = Query[ModelTags, ModelTags#TableElementType, Seq]

  implicit class withModelTagsJoinQuery[M, U, C[_]](modelTags: ModelTags.TableQuery)
      extends ModelTagsTableQuery[M, U, C](modelTags)

  /** List model tags given a page request
    *
    * @param pageRequest PageRequest information about sorting and page size
    */
  def listModelTags(pageRequest: PageRequest)
                   (implicit database: DB): Future[PaginatedResponse[ModelTag]] = {
    val modelTagQueryResult = database.db.run {
      val action = ModelTags.page(pageRequest).result
      logger.debug(s"Paginated Query for model tags -- SQL: ${action.statements.headOption}")
      action
    }
    val totalModelTagsQueryResult = database.db.run {
      val action = ModelTags.length.result
      logger.debug(s"Total Query for model tags -- SQL: ${action.statements.headOption}")
      action
    }

    for {
      totalTags <- totalModelTagsQueryResult
      tags <- modelTagQueryResult
    } yield {
      val hasNext = (pageRequest.offset + 1) * pageRequest.limit < totalTags // 0 indexed page offset
      val hasPrevious = pageRequest.offset > 0
      PaginatedResponse(totalTags,
        hasPrevious,
        hasNext,
        pageRequest.offset,
        pageRequest.limit,
        tags)
    }
  }

  /** Insert a model tag given a create case class with a user
    *
    * @param modelTagtoCreate ModelTag.Create object to use to create full model tag
    * @param userId           String user/owner to create a new model tag with
    */
  def insertModelTag(modelTagtoCreate: ModelTag.Create, userId: String)(
      implicit database: DB): Future[ModelTag] = {
    val modelTag = modelTagtoCreate.toModelTag(userId)
    val insertAction = ModelTags.forceInsert(modelTag)

    logger.debug(s"Inserting Model Tag -- SQL: ${insertAction.statements.headOption}")

    database.db.run {
      insertAction
    } map { _ =>
      modelTag
    }
  }

  /** Given a model tag ID, attempt to retrieve it from the database
    *
    * @param modelTagId UUID ID of model tag to get from database
    */
  def getModelTag(modelTagId: UUID)(implicit database: DB): Future[Option[ModelTag]] = {
    val fetchAction = ModelTags.filter(_.id === modelTagId).result.headOption

    database.db.run {
      fetchAction
    }
  }

  /** Delete a given model tag
    *
    * @param modelTagId UUID ID of model tag to delete
    */
  def deleteModelTag(modelTagId: UUID)(implicit database: DB): Future[Int] = {
    database.db.run {
      ModelTags.filter(_.id === modelTagId).delete
    }
  }

  /** Update a model tag's tag
    *
    * Note: Only updates the tag, while automatically setting the modification
    * time and who modified the tag
    *
    * @param modelTag ModelTag model tag to use for update
    * @param modelTagId UUID ID of tag to perform update with
    * @param user User user performing update
    */
  def updateModelTag(modelTag: ModelTag, modelTagId: UUID, user: User)(
      implicit database: DB): Future[Int] = {
    val updateTime = new Timestamp((new java.util.Date).getTime)

    val updateModelTagQuery = for {
      updateModelTag <- ModelTags.filter(_.id === modelTagId)
    } yield (updateModelTag.modifiedAt, updateModelTag.modifiedBy, updateModelTag.tag)

    database.db.run {
      updateModelTagQuery.update((updateTime, user.id, modelTag.tag))
    } map {
      case 1 => 1
      case _ => throw new IllegalStateException("Error while updating model tag")
    }
  }
}

class ModelTagsTableQuery[M, U, C[_]](modelTags: ModelTags.TableQuery) {
  def page(pageRequest: PageRequest): ModelTags.TableQuery = {
    ModelTags
      .drop(pageRequest.offset * pageRequest.limit)
      .take(pageRequest.limit)
  }
}
