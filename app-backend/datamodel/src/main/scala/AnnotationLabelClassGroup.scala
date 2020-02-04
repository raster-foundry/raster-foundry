package com.rasterfoundry.datamodel

import io.circe.generic.semiauto._
import io.circe._

import java.util.UUID

case class AnnotationLabelClassGroup(
    id: UUID,
    name: String,
    annotationProjectId: UUID,
    index: Int
)

object AnnotationLabelClassGroup {
  implicit val encAnnotationLabelClassGroup
      : Encoder[AnnotationLabelClassGroup] = deriveEncoder
  implicit val decAnnotationLabelClassGroup
      : Decoder[AnnotationLabelClassGroup] = deriveDecoder

  case class Create(
      name: String,
      index: Option[Int],
      classes: List[AnnotationLabelClass.Create]
  )

  object Create {
    implicit val decAnnotationLabelClassGroupCreate
        : Decoder[AnnotationLabelClassGroup.Create] = deriveDecoder
  }

}
