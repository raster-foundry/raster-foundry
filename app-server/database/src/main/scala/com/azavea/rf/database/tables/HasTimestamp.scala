package com.azavea.rf.database.tables

import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.database.query.TimestampQueryParameters

trait HasTimestamp  { self: Table[_] =>
  def createdAt: Rep[java.sql.Timestamp]
  def modifiedAt: Rep[java.sql.Timestamp]
}

object HasTimestamp {
  implicit class DefaultQuery[M <: HasTimestamp, U, C[_]](that: Query[M, U, Seq]) {
    def filterByTimestamp(timeParams: TimestampQueryParameters) = {
      that.filter{ rec =>
        List(
          timeParams.minCreateDatetime.map(rec.createdAt > _),
          timeParams.maxCreateDatetime.map(rec.createdAt < _),
          timeParams.minModifiedDatetime.map(rec.modifiedAt > _),
          timeParams.maxModifiedDatetime.map(rec.modifiedAt < _)
        ).flatten.reduceLeftOption(_ && _).getOrElse(true: Rep[Boolean])
      }
    }
  }
}
