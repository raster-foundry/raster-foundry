package com.rasterfoundry.database

import io.estatico.newtype.macros.newtype

import java.util.UUID

@SuppressWarnings(Array("AsInstanceOf"))
object types {
  // to differentiate copy operations between parent and child annotation projects
  @newtype case class ParentAnnotationProjectId(parentAnnotationProjectId: UUID)
  @newtype case class ChildAnnotationProjectId(childAnnotationProjectId: UUID)
}
