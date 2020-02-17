package com.rasterfoundry.datamodel

import io.circe._
import io.circe.generic.JsonCodec

import java.sql.Timestamp
import java.util.UUID

/** Model Lab Tool */
@JsonCodec
final case class Tool(
    id: UUID,
    createdAt: Timestamp,
    modifiedAt: Timestamp,
    createdBy: String,
    owner: String,
    title: String,
    description: String,
    requirements: String,
    license: Option[Int],
    visibility: Visibility,
    compatibleDataSources: List[String] = List.empty,
    stars: Float = 0.0f,
    definition: Json,
    singleSource: Boolean
)

/** Case class for tool creation */
object Tool {
  def create = Create.apply _

  def tupled = (Tool.apply _).tupled

  @JsonCodec
  final case class Create(
      title: String,
      description: String,
      requirements: String,
      license: Option[Int],
      visibility: Visibility,
      compatibleDataSources: List[String],
      owner: Option[String],
      stars: Float,
      definition: Json,
      singleSource: Boolean
  ) extends OwnerCheck
}
