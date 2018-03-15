package com.azavea.rf.database.fields

import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.datamodel._

trait TimestampFields  { self: Table[_] =>
  def createdAt: Rep[java.sql.Timestamp]
  def modifiedAt: Rep[java.sql.Timestamp]
}

object TimestampFields {
  implicit class DefaultQuery[M <: TimestampFields, U, C[_]](that: Query[M, U, Seq]) {
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
