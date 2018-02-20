package com.azavea.rf.database.tables

import java.sql.Timestamp
import java.util.UUID

import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.database.fields.{OrganizationFkFields, TimestampFields, UserFkFields}
import com.azavea.rf.datamodel.{Shape => ShapeModel, _}

import geotrellis.slick.Projected
import geotrellis.vector.Geometry
import com.lonelyplanet.akka.http.extensions.PageRequest
import com.typesafe.scalalogging.LazyLogging
import slick.model.ForeignKeyAction

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import io.circe.Json

/** Table that represents Shapes
  *
  * Shapes represent a geometry-name-description
  */
class Shapes(_tableTag: Tag) extends Table[ShapeModel](_tableTag, "shapes")
    with TimestampFields
    with OrganizationFkFields
    with UserFkFields
{
  def * = (id, createdAt, createdBy, modifiedAt, modifiedBy, owner,
    organizationId, name, description, geometry) <> (ShapeModel.tupled, ShapeModel.unapply)

  val id: Rep[java.util.UUID] = column[java.util.UUID]("id", O.PrimaryKey)
  val createdAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created_at")
  val createdBy: Rep[String] = column[String]("created_by", O.Length(255,varying=true))
  val modifiedAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("modified_at")
  val modifiedBy: Rep[String] = column[String]("modified_by", O.Length(255,varying=true))
  val owner: Rep[String] = column[String]("owner", O.Length(255,varying=true))
  val organizationId: Rep[java.util.UUID] = column[java.util.UUID]("organization_id")
  val name: Rep[String] = column[String]("name")
  val description: Rep[Option[String]] = column[Option[String]]("description")
  val geometry: Rep[Option[Projected[Geometry]]] = column[Option[Projected[Geometry]]]("geometry")

  lazy val organizationsFk = foreignKey("shapes_organization_id_fkey", organizationId, Organizations)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val createdByUserFK = foreignKey("shapes_created_by_fkey", createdBy, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val modifiedByUserFK = foreignKey("shapes_modified_by_fkey", modifiedBy, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val ownerUserFK = foreignKey("shapes_owner_fkey", owner, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
}

object Shapes extends TableQuery(tag => new Shapes(tag)) with LazyLogging {

  val tq = TableQuery[Shapes]
  type TableQuery = Query[Shapes, ShapeModel, Seq]


  implicit class withShapesTableQuery[M, U, C[_]](shapes: Shapes.TableQuery) extends
    ShapeTableQuery[M, U, C](shapes)

  /** List shapes given a page request
    *
    * @param offset Int offset of request for pagination
    * @param limit Int limit of objects per page
    * @param queryParams ShapeQueryparameters query parameters for request
    */
  def listShapes(offset: Int, limit: Int, queryParams: ShapeQueryParameters, user: User) = {

    val dropRecords = limit * offset
    val filteredShapes = Shapes
      .filterByUser(queryParams.userParams)
    val pagedShapes = filteredShapes
      .drop(dropRecords)
      .take(limit)

    ListQueryResult[ShapeModel](
      (pagedShapes.result):DBIO[Seq[ShapeModel]],
      filteredShapes.length.result
    )
  }

  /** Insert a Shape given a create case class with a user
    *
    * @param shapeToCreate Shape.Create object to use to create full Shape
    * @param user               User to create a new Shape with
    */
  def insertShape(shapeToCreate: ShapeModel.Create, user: User) = {
    val shape = shapeToCreate.toShape(user)
    (Shapes returning Shapes).forceInsert(shape)
  }

  /** Insert many Shapes given a secreate case class with a user
    *
    * @param shapesToCreate Seq[Shape.Create] to use to create full Shape
    * @param user                User to create a new Shape with
    */
  def insertShapes(shapesToCreate: Seq[ShapeModel.Create], user: User)
                       (implicit database: DB) = {
    val shapesToInsert = shapesToCreate map { _.toShape(user) }
    database.db.run {
      Shapes.forceInsertAll(
        shapesToInsert
      )
    } map { res =>
      shapesToInsert.map(_.toGeoJSONFeature)
    }
  }


  /** Given a Shape ID, attempt to retrieve it from the database
    *
    * @param shapeId UUID ID of shape to get from database
    * @param user     Results will be limited to user's organization
    */
  def getShape(shapeId: UUID, user: User) =
    Shapes
      .filter(_.id === shapeId)
      .filterToOwnerIfNotInRootOrganization(user)
      .result
      .headOption

  /** Given a Shape ID, attempt to remove it from the database
    *
    * @param shapeId UUID ID of shape to remove
    * @param user     Results will be limited to user's organization
    */
  def deleteShape(shapeId: UUID, user: User) =
    Shapes
      .filter(_.id === shapeId)
      .filterToOwnerIfNotInRootOrganization(user)
      .delete

  /** Update a Shape
    * @param shape Shape to use for update
    * @param shapeId UUID of Shape to update
    * @param user User to use to update Shape
    */
  def updateShape(shape: ShapeModel.GeoJSON, shapeId: UUID, user: User) = {
    val updateTime = new Timestamp((new java.util.Date).getTime)
    val updateShapeQuery = for {
        updateShape <- Shapes
                        .filter(_.id === shapeId)
                        .filterToOwnerIfNotInRootOrganization(user)
    } yield (
        updateShape.modifiedAt,
        updateShape.modifiedBy,
        updateShape.name,
        updateShape.description,
        updateShape.geometry
    )

    updateShapeQuery.update(
        updateTime, // modifiedAt
        user.id, // modifiedBy
        shape.properties.name,
        shape.properties.description,
        shape.geometry
    )
  }

}

class ShapeTableQuery[M, U, C[_]](shapes: Shapes.TableQuery) {
  def page(pageRequest: PageRequest): Shapes.TableQuery = {
    Shapes
      .drop(pageRequest.offset * pageRequest.limit)
      .take(pageRequest.limit)
  }
}
