package com.rasterfoundry.datamodel

import io.circe.generic.semiauto._
import io.circe._

import java.util.UUID

final case class AnnotationLabelClassGroup(
    id: UUID,
    name: String,
    annotationProjectId: UUID,
    index: Int
) {
  def withLabelClasses(
      classes: List[AnnotationLabelClass]
  ): AnnotationLabelClassGroup.WithRelated =
    AnnotationLabelClassGroup.WithRelated(
      id,
      name,
      annotationProjectId,
      index,
      classes
    )
}

object AnnotationLabelClassGroup {
  implicit val encAnnotationLabelClassGroup
      : Encoder[AnnotationLabelClassGroup] = deriveEncoder
  implicit val decAnnotationLabelClassGroup
      : Decoder[AnnotationLabelClassGroup] = deriveDecoder

  final case class Create(
      name: String,
      index: Option[Int],
      classes: List[AnnotationLabelClass.Create]
  )

  object Create {
    implicit val decAnnotationLabelClassGroupCreate
        : Decoder[AnnotationLabelClassGroup.Create] = deriveDecoder
  }

  final case class WithRelated(
      id: UUID,
      name: String,
      annotationProjectId: UUID,
      index: Int,
      labelClasses: List[AnnotationLabelClass]
  )

  object WithRelated {
    implicit val encWithRelated: Encoder[WithRelated] = deriveEncoder
  }
}
