package com.azavea.rf.datamodel

import java.util.UUID
import java.sql.Timestamp

import io.circe._
import io.circe.generic.JsonCodec
import io.circe.syntax._

@JsonCodec
case class Platform(
  id: UUID,
  createdAt: Timestamp,
  createdBy: String,
  modifiedAt: Timestamp,
  modifiedBy: String,
  name: String,
  settings: Json,
  isActive: Boolean
)

object Platform {
  def create = Create.apply _
  def tupled = (Platform.apply _).tupled

  @JsonCodec
  case class Create(
    name: String,
    settings: Json = "{}".asJson
  ) {
    def toPlatform(user: User): Platform = {
      val now = new Timestamp((new java.util.Date()).getTime())

      Platform(
        UUID.randomUUID(),
        now, // createdAt
        user.id, // createdBy
        now, // modifiedAt
        user.id, // modifiedBy
        name,
        settings,
        true //isActive
      )
    }
  }
}
