package com.azavea.rf.database.meta

import java.time.LocalDate
import java.util.UUID

import doobie._, doobie.implicits._

trait BatchMeta {
  implicit val localDateMeta: Meta[LocalDate] =
    Meta[String].xmap(LocalDate.parse, _.toString)

  implicit val uuidMeta: Meta[UUID] =
    Meta[String].xmap(UUID.fromString, _.toString)
}
