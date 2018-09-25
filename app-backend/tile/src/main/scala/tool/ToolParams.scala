package com.azavea.rf.tile.tool

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import geotrellis.raster.render.{ColorRamps, ColorRamp}

import scala.util.Try

case class ToolParams(
    bands: Array[Int],
    breaks: Array[Double],
    ramp: ColorRamp
)

object ToolParams {
  def toolParams(
      defaultColorRamp: Option[ColorRamp] = None,
      defaultBreaks: Option[Array[Double]] = None): Directive1[ToolParams] =
    (bandsParam('bands) & colorBreaksParam('breaks, defaultBreaks) & colorRampParam(
      'ramp,
      defaultColorRamp)).as(ToolParams.apply _)

  def subsetBands: Directive1[Option[Array[Int]]] =
    parameters('bands.?).as { bands: Option[String] =>
      for (b <- bands) yield b.split(",").map(_.toInt)
    }

  /**
    * Produce a color ramp or correct exception if not possible.
    *
    * @param name     Name of the URL parameters
    * @param default  Default ColorMap if parameter is not set or can't be parsed
    */
  def colorRampParam(name: Symbol,
                     default: Option[ColorRamp]): Directive1[ColorRamp] = {
    def getColorRamp(str: String): Option[ColorRamp] = {
      val chunks = str.split(",").map(_.trim)
      if (chunks.length > 1)
        Try(ColorRamp(chunks.map(_.toInt))).toOption
      else
        Option {
          str match {
            case "BlueToOrange" =>
              ColorRamps.BlueToOrange
            case "BlueToRed" =>
              ColorRamps.BlueToRed
            case "LightToDarkGreen" =>
              ColorRamps.LightToDarkGreen
            case _ => null
            // TODO: List all options in ColorRamps
          }
        }
    }

    parameters(name.as[String].?).as { str: Option[String] =>
      (str.flatMap(getColorRamp), default) match {
        case (Some(ramp), _)    => ramp
        case (None, Some(ramp)) => ramp
        case (None, None) =>
          throw new IllegalArgumentException(
            s"$name parameter could be parsed into a ColorRamp")
      }
    }
  }

  /**
    * Produce array of value breaks or exception of not possible
    *
    * @param name     Name of the URL parameters
    * @param default  Default breaks if parameter is not set or can't be parsed
    */
  def colorBreaksParam(
      name: Symbol,
      default: Option[Array[Double]] = None): Directive1[Array[Double]] =
    parameters(name.as[String].?).as { str: Option[String] =>
      (str.flatMap(str =>
         Try(str.split(",").map(_.trim.toDouble).sorted).toOption),
       default) match {
        case (Some(ramp), _)    => ramp
        case (None, Some(ramp)) => ramp
        case (None, None) =>
          throw new IllegalArgumentException(
            s"$name parameter could be parsed into a Array[Double]")
      }
    }

  /**
    * Produce array of desired bands, used to re-order stored multiband tile.
    *
    * @param name     Name of the URL parameters
    */
  def bandsParam(name: Symbol): Directive1[Array[Int]] =
    parameters(name.as[String]).as { str: String =>
      str.split(",").map(_.trim.toInt)
    }

}
