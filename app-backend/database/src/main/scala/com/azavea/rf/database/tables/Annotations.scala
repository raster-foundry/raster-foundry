package com.azavea.rf.database.tables

import java.sql.Timestamp
import java.util.UUID

import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.database.fields.{OrganizationFkFields, TimestampFields, UserFkFields}
import com.azavea.rf.datamodel._

import geotrellis.slick.Projected
import geotrellis.vector.Geometry
import com.lonelyplanet.akka.http.extensions.PageRequest
import com.typesafe.scalalogging.LazyLogging
import slick.model.ForeignKeyAction

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import io.circe.Json

/** Table that represents annotations
  *
  * Annotations represent a geometry-label that identifies a real-world object
  */
class Annotations(_tableTag: Tag) extends Table[Annotation](_tableTag, "annotations")
    with TimestampFields
    with OrganizationFkFields
    with UserFkFields
{
  def * = (id, projectId, createdAt, createdBy, modifiedAt, modifiedBy, owner,
    organizationId, label, description, machineGenerated, confidence, quality,
    geometry) <> (
    Annotation.tupled, Annotation.unapply
  )

  val id: Rep[java.util.UUID] = column[java.util.UUID]("id", O.PrimaryKey)
  val projectId: Rep[java.util.UUID] = column[java.util.UUID]("project_id")
  val createdAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created_at")
  val createdBy: Rep[String] = column[String]("created_by", O.Length(255,varying=true))
  val modifiedAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("modified_at")
  val modifiedBy: Rep[String] = column[String]("modified_by", O.Length(255,varying=true))
  val owner: Rep[String] = column[String]("owner", O.Length(255,varying=true))
  val organizationId: Rep[java.util.UUID] = column[java.util.UUID]("organization_id")
  val label: Rep[String] = column[String]("label")
  val description: Rep[Option[String]] = column[Option[String]]("description")
  val machineGenerated: Rep[Option[Boolean]] = column[Option[Boolean]]("machine_generated", O.Default(None))
  val confidence: Rep[Option[Double]] = column[Option[Double]]("confidence")
  val quality: Rep[Option[AnnotationQuality]] = column[Option[AnnotationQuality]]("quality")
  val geometry: Rep[Option[Projected[Geometry]]] = column[Option[Projected[Geometry]]]("geometry")

  lazy val organizationsFk = foreignKey("annotations_organization_id_fkey", organizationId, Organizations)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val createdByUserFK = foreignKey("annotations_created_by_fkey", createdBy, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val modifiedByUserFK = foreignKey("annotations_modified_by_fkey", modifiedBy, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val ownerUserFK = foreignKey("annotations_owner_fkey", owner, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val projectFk = foreignKey("annotations_project_fkey", projectId, Projects)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
}

object Annotations extends TableQuery(tag => new Annotations(tag)) with LazyLogging {

  val tq = TableQuery[Annotations]
  type TableQuery = Query[Annotations, Annotation, Seq]


  implicit class withAnnotationsTableQuery[M, U, C[_]](annotations: Annotations.TableQuery) extends
    AnnotationTableQuery[M, U, C](annotations)

  /** List annotations given a page request
    *
    * @param offset Int offset of request for pagination
    * @param limit Int limit of objects per page
    * @param queryParams AnnotationQueryparameters query parameters for request
    */
  def listAnnotations(offset: Int, limit: Int, queryParams: AnnotationQueryParameters, projectId: UUID, user: User) = {

    val dropRecords = limit * offset
    val filteredAnnotations = Annotations
      .filterByUser(queryParams.userParams)
      .filter(_.projectId === projectId)
      .filterByAnnotationParams(queryParams)
    val pagedAnnotations = filteredAnnotations
         .drop(dropRecords)
         .take(limit)

    ListQueryResult[Annotation](
      (pagedAnnotations.result):DBIO[Seq[Annotation]],
      filteredAnnotations.length.result
    )
  }

  /** Insert a Annotation given a create case class with a user
    *
    * @param annotationToCreate Annotation.Create object to use to create full Annotation
    * @param user               User to create a new Annotation with
    */
  def insertAnnotation(annotationToCreate: Annotation.Create, projectId: UUID, user: User) = {
    val annotation = annotationToCreate.toAnnotation(projectId, user)
    (Annotations returning Annotations).forceInsert(annotation)
  }

  /** Insert many Annotations given a secreate case class with a user
    *
    * @param annotationsToCreate Seq[Annotation.Create] to use to create full Annotation
    * @param user                User to create a new Annotation with
    */
  def insertAnnotations(annotationsToCreate: Seq[Annotation.Create], projectId: UUID, user: User)
                       (implicit database: DB) = {
    val annotationsToInsert = annotationsToCreate map { _.toAnnotation(projectId, user) }
    database.db.run {
      Annotations.forceInsertAll(
        annotationsToInsert
      )
    } map { res =>
      annotationsToInsert.map(_.toGeoJSONFeature)
    }
  }


  /** Given a Annotation ID, attempt to retrieve it from the database
    *
    * @param annotationId UUID ID of annotation to get from database
    * @param user     Results will be limited to user's organization
    */
  def getAnnotation(annotationId: UUID, user: User) =
    Annotations
      .filterToSharedOrganizationIfNotInRoot(user)
      .filter(_.id === annotationId)
      .result
      .headOption

  /** Given a Project ID, attempt to list all applicable labels
    */
  def listProjectLabels(projectId: UUID, user: User)(implicit database: DB) = {
    database.db.run {
      Annotations
        .filterToSharedOrganizationIfNotInRoot(user)
        .filter(_.projectId === projectId)
        .map(_.label)
        .distinct
        .result
    }
  }

  /** Given a Annotation ID, attempt to remove it from the database
    *
    * @param annotationId UUID ID of annotation to remove
    * @param user     Results will be limited to user's organization
    */
  def deleteAnnotation(annotationId: UUID, user: User) =
    Annotations
      .filterToSharedOrganizationIfNotInRoot(user)
      .filter(_.id === annotationId)
      .delete

  /** Given a Project ID, attempt to delete all associated annotations from the database
    *
    * @param projectId UUID ID of project to remove annotations from
    * @param user     Results will be limited to user's organization
    */
  def deleteProjectAnnotations(projectId: UUID, user: User) =
    Annotations
      .filterToSharedOrganizationIfNotInRoot(user)
      .filter(_.projectId === projectId)
      .delete

  /** Update a Annotation
    * @param annotation Annotation to use for update
    * @param annotationId UUID of Annotation to update
    * @param user User to use to update Annotation
    */
  def updateAnnotation(annotation: Annotation.GeoJSON, annotationId: UUID, user: User) = {
    val updateTime = new Timestamp((new java.util.Date).getTime)
    val updateAnnotationQuery = for {
        updateAnnotation <- Annotations
                                .filterToSharedOrganizationIfNotInRoot(user)
                                .filter(_.id === annotationId)
    } yield (
        updateAnnotation.modifiedAt,
        updateAnnotation.modifiedBy,
        updateAnnotation.label,
        updateAnnotation.description,
        updateAnnotation.machineGenerated,
        updateAnnotation.confidence,
        updateAnnotation.quality,
        updateAnnotation.geometry
    )

    updateAnnotationQuery.update(
        updateTime, // modifiedAt
        user.id, // modifiedBy
        annotation.properties.label,
        annotation.properties.description,
        annotation.properties.machineGenerated,
        annotation.properties.confidence,
        annotation.properties.quality,
        annotation.geometry
    )
  }

}

class AnnotationTableQuery[M, U, C[_]](annotations: Annotations.TableQuery) {

  def filterByAnnotationParams(queryParams: AnnotationQueryParameters): Annotations.TableQuery = {
      val filteredByLabel = queryParams.label match {
          case Some(label) => annotations.filter(_.label === label)
          case _           => annotations
      }

      val filteredByMachineGenerated = queryParams.machineGenerated match {
          case Some(mg) => filteredByLabel.filter(_.machineGenerated === mg)
          case _        => filteredByLabel
      }

      val filteredByMinConfidence = queryParams.minConfidence match {
          case Some(mc) => filteredByMachineGenerated.filter(_.confidence >= mc)
          case _        => filteredByMachineGenerated
      }

      val filteredByMaxConfidence = queryParams.maxConfidence match {
          case Some(mc) => filteredByMinConfidence.filter(_.confidence <= mc)
          case _        => filteredByMinConfidence
      }

      queryParams.quality match {
          case Some(quality) => filteredByMaxConfidence.filter(_.quality === AnnotationQuality.fromString(quality))
          case _             => filteredByMaxConfidence
      }
  }

  def page(pageRequest: PageRequest): Annotations.TableQuery = {
    Annotations
      .drop(pageRequest.offset * pageRequest.limit)
      .take(pageRequest.limit)
  }
}
