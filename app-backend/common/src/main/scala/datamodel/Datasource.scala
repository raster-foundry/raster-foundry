package com.rasterfoundry.common.datamodel

import java.sql.Timestamp
import java.util.UUID

import io.circe._
import io.circe.generic.JsonCodec

@JsonCodec
final case class Datasource(id: UUID,
                            createdAt: java.sql.Timestamp,
                            createdBy: String,
                            modifiedAt: java.sql.Timestamp,
                            modifiedBy: String,
                            owner: String,
                            name: String,
                            visibility: Visibility,
                            composites: Map[String, ColorComposite],
                            extras: Json,
                            bands: Json,
                            licenseName: Option[String]) {
  def toThin: Datasource.Thin =
    Datasource.Thin(this.bands, this.composites, this.name, this.id)

  def defaultColorComposite: Option[ColorComposite] =
    this.composites
      .filter(_._1.toLowerCase.contains("natural"))
      .values
      .headOption orElse this.composites
      .filter(_._1.toLowerCase.contains("default"))
      .values
      .headOption orElse this.composites.values.headOption
}

object Datasource {

  def tupled = (Datasource.apply _).tupled

  def create = Create.apply _

  @JsonCodec
  final case class Thin(bands: Json,
                        composites: Map[String, ColorComposite],
                        name: String,
                        id: UUID)

  @JsonCodec
  final case class Create(name: String,
                          visibility: Visibility,
                          owner: Option[String],
                          composites: Map[String, ColorComposite],
                          extras: Json,
                          bands: Json,
                          licenseName: Option[String])
      extends OwnerCheck {
    def toDatasource(user: User): Datasource = {
      val id = java.util.UUID.randomUUID()
      val now = new Timestamp(new java.util.Date().getTime)

      val ownerId = checkOwner(user, this.owner)

      Datasource(
        id,
        now, // createdAt
        user.id, // createdBy
        now, // modifiedAt
        user.id, // modifiedBy
        ownerId, // owner
        this.name,
        this.visibility,
        this.composites,
        this.extras,
        this.bands,
        this.licenseName
      )
    }
  }
}
