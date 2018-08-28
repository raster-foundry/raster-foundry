package com.azavea.rf.datamodel

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
                            composites: Json,
                            extras: Json,
                            bands: Json,
                            licenseName: Option[String]) {
  def toThin: Datasource.Thin = Datasource.Thin(this.bands, this.name, this.id)
}

object Datasource {

  def tupled = (Datasource.apply _).tupled

  def create = Create.apply _

  @JsonCodec
  final case class Thin(bands: Json, name: String, id: UUID)

  @JsonCodec
  final case class Create(name: String,
                          visibility: Visibility,
                          owner: Option[String],
                          composites: Json,
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
