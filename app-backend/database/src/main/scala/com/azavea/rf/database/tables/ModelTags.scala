package com.azavea.rf.database.tables

import java.sql.Timestamp
import java.util.UUID

import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.database.fields.{OrganizationFkFields, TimestampFields, UserFkFields}
import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.datamodel._
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
}
