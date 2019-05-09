package com.rasterfoundry.common.utils

import com.rasterfoundry.datamodel.{Annotation, AnnotationQuality}
import geotrellis.geotools._
import geotrellis.proj4.{CRS, WebMercator}
import geotrellis.vector.reproject.Reproject
import geotrellis.vector.{Geometry, Projected, io => _}
import io.circe.parser._
import org.geotools.referencing.{CRS => geotoolsCRS}
import org.opengis.feature.Property
import org.opengis.feature.simple.SimpleFeature
import org.opengis.referencing.crs.CoordinateReferenceSystem

object Shapefile {

  @SuppressWarnings(Array("NullParameter"))
  def fromSimpleFeatureWithProps(sf: SimpleFeature,
                                 fields: Map[String, String],
                                 userId: String,
                                 prj: String): Option[Annotation.Create] = {
    // get the projection from the passed string in .prj file
    // then get its EPSG code to retrieve its CRS in geotrellis as the start CRS
    val startCsr: CoordinateReferenceSystem = geotoolsCRS.parseWKT(prj)
    val startEpsgCode: Int =
      geotoolsCRS.lookupIdentifier(startCsr, true).replace("EPSG:", "").toInt
    val geom: Geometry = sf.toGeometry[Geometry]
    val projected = Projected(
      Reproject(geom, CRS.fromEpsgCode(startEpsgCode), WebMercator),
      3857
    )

    val labelPropName = fields.getOrElse("label", null)
    val desPropName = fields.getOrElse("description", null)
    val machinePropName = fields.getOrElse("isMachine", null)

    val label = labelPropName match {
      case null => "Unlabeled"
      case _    => sf.getProperty(labelPropName).getValue.toString
    }
    val description = desPropName match {
      case null => Some("No Description")
      case _    => Some(sf.getProperty(desPropName).getValue.toString)
    }
    val (isMachine, confidence, quality) = machinePropName match {
      case null => (Some(false), None, None)
      case _ =>
        val isM = Option(sf.getProperty(machinePropName)) flatMap {
          p: Property =>
            p.getValue.toString match {
              // Apparently ogr2ogr stores bools as ints /shrug
              case "1" => Some(true)
              case "0" => Some(false)
              case _   => None
            }
        }

        val confPropName = fields.getOrElse("confidence", null)
        val conf = confPropName match {
          case null => None
          case _ =>
            Option(sf.getProperty(confPropName)) flatMap { p: Property =>
              decode[Float](p.getValue.toString).toOption
            }
        }

        val quaPropName = fields.getOrElse("quality", null)
        val qua = quaPropName match {
          case null => None
          case _ =>
            Option(sf.getProperty(quaPropName)) flatMap { p: Property =>
              decode[AnnotationQuality](p.getValue.toString).toOption
            }
        }
        (isM, conf, qua)
    }

    // annotationGroup is passed None in here since it will be handled
    // in insertAnnotations in AnnotationDao
    Some(
      Annotation.Create(
        Some(userId),
        label,
        description,
        isMachine,
        confidence,
        quality,
        Some(projected),
        None
      )
    )
  }
  @SuppressWarnings(Array("ListAppend"))
  def accumulateFeatures[T1, T2](
      f: (T1, Map[String, String], String, String) => Option[T2])(
      accum: List[T2],
      errorIndices: List[Int],
      accumulateFrom: List[T1],
      props: Map[String, String],
      userId: String,
      prj: String): Either[List[Int], List[T2]] =
    accumulateFrom match {
      case Nil =>
        errorIndices match {
          case Nil => Right(accum)
          case _   => Left(errorIndices)
        }
      case h +: t =>
        f(h, props, userId, prj) match {
          case Some(t2) =>
            accumulateFeatures(f)(
              accum :+ t2,
              errorIndices,
              t,
              props,
              userId,
              prj
            )
          case None =>
            accumulateFeatures(f)(
              accum,
              errorIndices :+ (accum.length + errorIndices.length + 1),
              t,
              props,
              userId,
              prj
            )
        }
    }
}
