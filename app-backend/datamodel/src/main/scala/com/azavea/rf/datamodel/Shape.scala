package com.azavea.rf.datamodel

import java.util.UUID
import java.sql.Timestamp

import io.circe._
import io.circe.generic.JsonCodec
import io.circe.generic.extras._

import geotrellis.slick.Projected
import geotrellis.vector.Geometry
import java.security.InvalidParameterException

@JsonCodec
case class Shape(
  id: UUID,
  createdAt: Timestamp,
  createdBy: String,
  modifiedAt: Timestamp,
  modifiedBy: String,
  owner: String,
  name: String,
  description: Option[String],
  geometry: Projected[Geometry]
) extends GeoJSONSerializable[Shape.GeoJSON] {
    def toGeoJSONFeature: Shape.GeoJSON = {
        Shape.GeoJSON(
            this.id,
            Some(this.geometry),
            ShapeProperties(
                this.createdAt,
                this.createdBy,
                this.modifiedAt,
                this.modifiedBy,
                this.owner,
                this.name,
                this.description
            ),
            "Feature"
        )
    }
}

@JsonCodec
case class ShapeProperties(
    createdAt: Timestamp,
    createdBy: String,
    modifiedAt: Timestamp,
    modifiedBy: String,
    owner: String,
    name: String,
    description: Option[String]
)

@JsonCodec
case class ShapePropertiesCreate(
    owner: Option[String],
    name: String,
    description: Option[String]
)


object Shape {

    implicit val config: Configuration = Configuration.default.copy(
      transformMemberNames = {
        case "_type" => "type"
        case other => other
      }
    )

    def tupled = (Shape.apply _).tupled
    def create = Create.apply _


    @ConfiguredJsonCodec
    case class GeoJSON(
        id: UUID,
        geometry: Option[Projected[Geometry]],
        properties: ShapeProperties,
        _type: String = "Feature"
    ) extends GeoJSONFeature {
        def toShape: Shape = {
          geometry match {
            case Some(g) =>
              Shape(
                id,
                properties.createdAt,
                properties.createdBy,
                properties.modifiedAt,
                properties.modifiedBy,
                properties.owner,
                properties.name,
                properties.description,
                g
              )
            case _ =>
              throw new InvalidParameterException("Shapes must have a geometry defined")
          }
        }
    }

    @JsonCodec
    case class Create(
        owner: Option[String],
        name: String,
        description: Option[String],
        geometry: Projected[Geometry]
    ) extends OwnerCheck {

        def toShape(user: User): Shape = {
            val now = new Timestamp((new java.util.Date()).getTime())
            val ownerId = checkOwner(user, this.owner)
            Shape(
                UUID.randomUUID, // id
                now, // createdAt
                user.id, // createdBy
                now, // modifiedAt
                user.id, // modifiedBy
                ownerId, // owner
                name,
                description,
                geometry
            )
        }
    }

    @JsonCodec
    case class GeoJSONFeatureCreate(
        geometry: Projected[Geometry],
        properties: ShapePropertiesCreate
    ) extends OwnerCheck {
        def toShapeCreate(): Shape.Create = {
            Shape.Create(
                properties.owner,
                properties.name,
                properties.description,
                geometry
            )
        }
    }
}
