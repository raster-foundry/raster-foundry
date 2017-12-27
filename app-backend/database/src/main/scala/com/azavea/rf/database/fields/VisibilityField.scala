package com.azavea.rf.database.fields

import java.util.UUID

import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.datamodel.Visibility

trait VisibilityField  { self: Table[_] =>
  val id: Rep[UUID]
  val visibility: Rep[Visibility]
}

object VisibilityField {
  implicit class DefaultQuery[M <: VisibilityField, U, C[_]](that: Query[M, U, Seq]) {
    def filterToPublic() = that.filter { rec =>
      rec.visibility === Visibility.fromString("PUBLIC")
    }
  }
}
