package com.azavea.rf.database.fields

import java.util.UUID

import com.azavea.rf.database.ExtendedPostgresDriver.api._

trait NameField  { self: Table[_] =>
  def id: Rep[UUID]
  def name: Rep[String]
}

object NameField {
  implicit class DefaultQuery[M <: NameField, U, C[_]](that: Query[M, U, Seq]) {
    def filterByName(name: String) = {
      that.filter(_.name === name)
    }
  }
}
