package com.azavea.rf.datamodel

import cats.implicits._

import io.circe._
import io.circe.generic.JsonCodec
import io.circe.syntax._
import io.circe.generic.extras._
import io.circe.parser._

import java.util.{UUID, Map => JMap, HashMap => JHashMap}
import java.sql.Timestamp

import scala.collection.JavaConverters._

import com.typesafe.scalalogging.LazyLogging

@JsonCodec
case class AnnotationGroup(
  id: UUID,
  name: String,
  createdAt: Timestamp,
  createdBy: String,
  modifiedAt: Timestamp,
  modifiedBy: String,
  projectId: UUID,
  defaultStyle: Option[Json]
)

object AnnotationGroup {
  @JsonCodec
  case class Create(
    name: String,
    defaultStyle: Option[Json]
  ) {
    def toAnnotationGroup(projectId: UUID, user: User): AnnotationGroup = {
      val now = new Timestamp((new java.util.Date()).getTime())
      AnnotationGroup(
        UUID.randomUUID,
        name,
        now,
        user.id,
        now,
        user.id,
        projectId,
        defaultStyle
      )
    }
  }
}
