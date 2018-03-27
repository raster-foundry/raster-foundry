package com.azavea.rf.database.meta

import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import doobie.util.invariant.InvalidObjectMapping
import cats._
import cats.data._
import cats.effect.IO
import cats.syntax.either._
import com.azavea.rf.datamodel.{Band, ColorCorrect, Image, Thumbnail, Tag, Category, Analysis}
import io.circe._
import io.circe.syntax._
import org.postgresql.util.PGobject
import com.azavea.rf.datamodel.ColorCorrect._
import com.azavea.rf.datamodel.Credential
import java.net.URI

trait CirceJsonbMeta {
  implicit val credentialMeta: Meta[Credential] =
    Meta.other[String]("text").xmap[Credential](
      a => {
        if (a.length == 0) Credential(None) else Credential.fromString(a)
      },
      a => a.token.getOrElse("").toString
    )

  implicit val jsonbMeta: Meta[Json] =
  Meta.other[PGobject]("jsonb").xmap[Json](
    a => io.circe.parser.parse(a.getValue).leftMap[Json](e => throw e).merge, // failure raises an exception
    a => {
      val o = new PGobject
      o.setType("jsonb")
      o.setValue(a.noSpaces)
      o
    }
  )

  implicit val colorCorrectionMeta: Meta[ColorCorrect.Params] = {
    Meta.other[PGobject]("jsonb").xmap[ColorCorrect.Params](
      a => io.circe.parser.parse(a.getValue).leftMap[Json](e => throw e).merge.as[ColorCorrect.Params] match {
        case Right(p) => p
        case Left(e) => throw e
      },
      a => {
        val o = new PGobject
        o.setType("jsonb")
        o.setValue(a.asJson.noSpaces)
        o
      }
    )
  }


  implicit val thumbnailMeta: Meta[List[Thumbnail]] = {
    Meta.other[PGobject]("jsonb").xmap[List[Thumbnail]](
      a => io.circe.parser.parse(a.getValue).leftMap[Json](e => throw e).merge.as[List[Thumbnail]] match {
        case Right(p) => p
        case Left(e) => throw e
      }, // failure raises an exception
      a => {
        val o = new PGobject
        o.setType("jsonb")
        o.setValue(a.asJson.noSpaces)
        o
      }
    )
  }

  implicit val tagMeta: Meta[List[Tag]] = {
    Meta.other[PGobject]("jsonb").xmap[List[Tag]](
      a => io.circe.parser.parse(a.getValue).leftMap[Json](e => throw e).merge.as[List[Tag]] match {
        case Right(p) => p
        case Left(e) => throw e
      }, // failure raises an exception
      a => {
        val o = new PGobject
        o.setType("jsonb")
        o.setValue(a.asJson.noSpaces)
        o
      }
    )
  }

  implicit val categoryMeta: Meta[List[Category]] = {
    Meta.other[PGobject]("jsonb").xmap[List[Category]](
      a => io.circe.parser.parse(a.getValue).leftMap[Json](e => throw e).merge.as[List[Category]] match {
        case Right(p) => p
        case Left(e) => throw e
      }, // failure raises an exception
      a => {
        val o = new PGobject
        o.setType("jsonb")
        o.setValue(a.asJson.noSpaces)
        o
      }
    )
  }

  implicit val analysisMeta: Meta[List[Analysis]] = {
    Meta.other[PGobject]("jsonb").xmap[List[Analysis]](
      a => io.circe.parser.parse(a.getValue).leftMap[Json](e => throw e).merge.as[List[Analysis]] match {
        case Right(p) => p
        case Left(e) => throw e
      }, // failure raises an exception
      a => {
        val o = new PGobject
        o.setType("jsonb")
        o.setValue(a.asJson.noSpaces)
        o
      }
    )
  }

  implicit val imageWithRelated: Meta[List[Image.WithRelated]] = {
    Meta.other[PGobject]("jsonb").xmap[List[Image.WithRelated]](
      a => io.circe.parser.parse(a.getValue).leftMap[Json](e => throw e).merge.as[List[Image.WithRelated]] match {
        case Right(p) => p
        case Left(e) => throw e
      }, // failure raises an exception
      a => {
        val o = new PGobject
        o.setType("jsonb")
        o.setValue(a.asJson.noSpaces)
        o
      }
    )
  }


  implicit val bandMeta: Meta[List[Band]] = {
    Meta.other[PGobject]("jsonb").xmap[List[Band]](
      a => io.circe.parser.parse(a.getValue).leftMap[Json](e => throw e).merge.as[List[Band]] match {
        case Right(p) => p
        case Left(e) => throw e
      }, // failure raises an exception
      a => {
        val o = new PGobject
        o.setType("jsonb")
        o.setValue(a.asJson.noSpaces)
        o
      }
    )
  }

  implicit val uriMeta: Meta[URI] = {
    Meta.other[String]("text").xmap[URI](
      a => new URI(a),
      a => a.toString
    )
  }
}

