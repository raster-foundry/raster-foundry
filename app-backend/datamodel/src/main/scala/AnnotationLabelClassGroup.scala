package com.rasterfoundry.datamodel

import io.circe._
import io.circe.generic.semiauto._

import java.util.UUID

final case class AnnotationLabelClassGroup(
    id: UUID,
    name: String,
    annotationProjectId: Option[UUID],
    campaignId: Option[UUID],
    index: Int,
    isActive: Boolean = true
) {
  def withLabelClasses(
      classes: List[AnnotationLabelClass]
  ): AnnotationLabelClassGroup.WithLabelClasses =
    AnnotationLabelClassGroup.WithLabelClasses(
      id,
      name,
      annotationProjectId,
      campaignId,
      index,
      isActive,
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
      classes: List[AnnotationLabelClass.Create],
      isActive: Boolean = true
  )

  object Create {
    implicit val decAnnotationLabelClassGroupCreate
      : Decoder[AnnotationLabelClassGroup.Create] = deriveDecoder
  }

  final case class WithLabelClasses(
      id: UUID,
      name: String,
      annotationProjectId: Option[UUID],
      campaignId: Option[UUID],
      index: Int,
      isActive: Boolean = true,
      labelClasses: List[AnnotationLabelClass]
  ) {
    def toClassGroup =
      AnnotationLabelClassGroup(
        id,
        name,
        annotationProjectId,
        campaignId,
        index,
        isActive
      )
  }

  object WithLabelClasses {
    implicit val encWithLabelClasses: Encoder[WithLabelClasses] = deriveEncoder
    implicit val decWithLabelClasses: Decoder[WithLabelClasses] = deriveDecoder
  }
}
