package com.azavea.rf.database.meta

import com.azavea.rf.datamodel.SingleBandOptions

import doobie._, doobie.implicits._
import cats._, cats.data._, cats.effect.IO
import cats.syntax.either._
import doobie.postgres._, doobie.postgres.implicits._
import doobie.util.invariant.InvalidObjectMapping
import org.postgis.jts._
import com.vividsolutions.jts.geom
import doobie.scalatest.imports._
import io.circe._
import io.circe.syntax._

import scala.reflect.runtime.universe.TypeTag
import scala.reflect.ClassTag

trait SingleBandOptionsMeta extends CirceJsonbMeta {
  implicit val singleBandOptionsMeta: Meta[SingleBandOptions.Params] =
    Meta[Json].xmap[SingleBandOptions.Params](
      _.as[SingleBandOptions.Params] match {
        case Right(ast) => ast
        case Left(e)    => throw e
      },
      _.asJson
    )
}
