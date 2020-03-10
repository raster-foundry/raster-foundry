package com.rasterfoundry.datamodel

import io.circe._
import io.circe.generic.semiauto._

import java.util.UUID

final case class AnnotationLabelClass(
    id: UUID,
    name: String,
    annotationLabelClassGroupId: UUID,
    colorHexCode: String,
    default: Option[Boolean],
    determinant: Option[Boolean],
    index: Int
)

object AnnotationLabelClass {
  implicit val encAnnotationLabelClass: Encoder[AnnotationLabelClass] =
    deriveEncoder
  implicit val decAnnotationLabelClass: Decoder[AnnotationLabelClass] =
    deriveDecoder

  final case class Create(
      name: String,
      colorHexCode: String,
      default: Option[Boolean],
      determinant: Option[Boolean],
      index: Int
  )

  object Create {
    implicit val decCreate: Decoder[Create] = deriveDecoder
  }
}
