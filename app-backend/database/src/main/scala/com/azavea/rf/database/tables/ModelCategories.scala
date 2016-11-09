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

/** Table that represents categories for models in model lab
  *
  * These are user generated categories for models to help users
  * track and search for them. For instance, users may decide to
  * create categories for coursework, projects, etc.
  */
class ModelCategories(_tableTag: Tag)
  extends Table[ModelCategory](_tableTag, "model_categories")
    with LazyLogging
    with UserFkFields
    with TimestampFields {

  def * =
    (id, createdAt, modifiedAt, createdBy, modifiedBy, category) <> (ModelCategory.tupled, ModelCategory.unapply)

  val id: Rep[UUID] = column[UUID]("id", O.PrimaryKey)
  val createdAt: Rep[Timestamp] = column[Timestamp]("created_at")
  val createdBy: Rep[String] = column[String]("created_by", O.Length(255, varying = true))
  val modifiedAt: Rep[Timestamp] = column[Timestamp]("modified_at")
  val modifiedBy: Rep[String] = column[String]("modified_by", O.Length(255, varying = true))
  val category: Rep[String] = column[String]("category")

  lazy val createdByUserFK = foreignKey("model_categories_created_by_fkey", createdBy, Users)(
    r => r.id,
    onUpdate = ForeignKeyAction.NoAction,
    onDelete = ForeignKeyAction.NoAction)
  lazy val modifiedByUserFK = foreignKey("model_categories_modified_by_fkey", modifiedBy, Users)(
    r => r.id,
    onUpdate = ForeignKeyAction.NoAction,
    onDelete = ForeignKeyAction.NoAction)

}

object ModelCategories extends TableQuery(tag => new ModelCategories(tag)) with LazyLogging {
  type TableQuery = Query[ModelCategories, ModelCategories#TableElementType, Seq]

  /** Insert a model category given a create case class with a user
    *
    * @param modelCategoryToCreate ModelCategory.Create object to use to create full model tag
    * @param userId           String user/owner to create a new model category with
    */
  def insertModelCategory(modelCategoryToCreate: ModelCategory.Create, userId: String)(
    implicit database: DB): Future[ModelCategory] = {
    val modelCategory = modelCategoryToCreate.toModelCategory(userId)
    val insertAction = ModelCategories.forceInsert(modelCategory)

    logger.debug(s"Inserting Model Category -- SQL: ${insertAction.statements.headOption}")

    database.db.run {
      insertAction
    } map { _ =>
      modelCategory
    }
  }

  /** Given a model category ID, attempt to retrieve it from the database
    *
    * @param modelCategoryId UUID ID of model category to get from database
    */
  def getModelCategory(modelCategoryId: UUID)(implicit database: DB): Future[Option[ModelCategory]] = {
    val fetchAction = ModelCategories.filter(_.id === modelCategoryId).result.headOption

    database.db.run {
      fetchAction
    }
  }
}
