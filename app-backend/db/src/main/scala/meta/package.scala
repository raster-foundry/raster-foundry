package com.rasterfoundry.database

import com.rasterfoundry.datamodel._

import cats.implicits._
import cats.data.NonEmptyList
import doobie._
import geotrellis.proj4.CRS
import geotrellis.raster.CellType
import io.circe.syntax._
import org.postgresql.util.PGobject

import scala.util.Try
import scala.reflect.runtime.universe.TypeTag

import java.net.URI
import java.time.LocalDate

package object meta {
  trait RFMeta
      extends GtWktMeta
      with CirceJsonbMeta
      with EnumMeta
      with PermissionsMeta {

    implicit val crsMeta: Meta[CRS] =
      Meta[String].timap(s =>
        Try { CRS.fromString(s) } getOrElse { CRS.fromName(s) })(
        _.toProj4String)

    implicit val cellTypeMeta: Meta[CellType] =
      Meta[String].timap(CellType.fromName)(CellType.toName)

    implicit val uriMeta: Meta[URI] =
      Meta[String].timap(URI.create)(_.toASCIIString)

    implicit val timeRangeMeta: Meta[(LocalDate, LocalDate)] =
      Meta.Advanced
        .other[PGobject]("tsrange")
        .timap[(LocalDate, LocalDate)](intervalString => {
          // We can't piggy-back on json here, since the format of the interval string makes
          // circe _extremely angry_ and we can't even construct an HCursor to write a
          // custom decoder.
          val (s1, s2) = intervalString.getValue
            .replace("\"", "")
            .replace(" 00:00:00", "")
            .replace("[", "")
            .replace(")", "")
            .split(",")
            .toList match {
            case h :: t :: Nil =>
              (h, t)
            case _ =>
              ("", "")
          }
          Either
            .catchNonFatal((LocalDate.parse(s1), LocalDate.parse(s2)))
            .leftMap[(LocalDate, LocalDate)](e => throw e)
            .merge
        })(a => {
          val o = new PGobject
          o.setType("tsrange")
          o.setValue(a.asJson.noSpaces.replace("\"", ""))
          o
        })

    implicit def nelMeta[A: TypeTag](
        implicit
        ev: Meta[List[A]]): Meta[NonEmptyList[A]] =
      ev.timap(_.toNel.getOrElse(throw new Exception(s"Empty non-empty list")))(
        _.toList
      )
  }

}
