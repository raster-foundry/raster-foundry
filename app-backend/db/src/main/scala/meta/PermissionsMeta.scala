package com.rasterfoundry.database.meta

import com.rasterfoundry.datamodel._
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
      .timap(ObjectAccessControlRule.unsafeFromObjAcrString)(_.toObjAcrString)
}
