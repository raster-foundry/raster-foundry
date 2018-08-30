package com.azavea.rf.database.meta

import com.azavea.rf.datamodel._
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import doobie.util.invariant.InvalidObjectMapping
import cats._
import cats.data._
import cats.effect.IO
import cats.syntax.either._
import io.circe._
import io.circe.syntax._

trait PermissionsMeta {
  implicit val ObjectAccessControlRuleMeta: Meta[ObjectAccessControlRule] =
    Meta[String].xmap(ObjectAccessControlRule.unsafeFromObjAcrString, _.toObjAcrString)
}
