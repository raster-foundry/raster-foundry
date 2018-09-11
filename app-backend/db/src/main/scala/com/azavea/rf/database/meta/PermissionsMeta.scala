package com.azavea.rf.database.meta

import com.azavea.rf.datamodel._
import cats._
import cats.effect.IO
import cats.implicits._
import doobie._
import doobie.implicits._
import io.circe._
import io.circe.jawn._
import io.circe.syntax._

trait PermissionsMeta {
  implicit val ObjectAccessControlRuleMeta: Meta[ObjectAccessControlRule] =
    Meta[String]
      .xmap(ObjectAccessControlRule.unsafeFromObjAcrString, _.toObjAcrString)
}
