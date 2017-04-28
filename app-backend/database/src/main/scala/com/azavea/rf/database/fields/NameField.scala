package com.azavea.rf.database.fields

import com.azavea.rf.database.ExtendedPostgresDriver.api._

trait NameField  { self: Table[_] =>
  def name: Rep[String]
}

object NameField {
  implicit class DefaultQuery[M <: NameField, U, C[_]](that: Query[M, U, Seq]) {
    def filterByName(name: String) = {
      that.filter(_.name === name)
    }
  }
}
