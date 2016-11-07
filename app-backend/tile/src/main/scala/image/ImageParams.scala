package com.azavea.rf.tile.image

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._

case class ImageParams(
  redBand: Int, greenBand: Int, blueBand: Int,
  redGamma: Option[Double], greenGamma: Option[Double], blueGamma: Option[Double],
  contrast: Option[Double], brightness: Option[Int],
  alpha: Option[Double], beta: Option[Double],
  min: Option[Int], max: Option[Int],
  equalize: Boolean
)

object ImageParams {
  def imageParams: Directive1[ImageParams] =
    parameters(
      'redBand.as[Int].?(0), 'greenBand.as[Int].?(1), 'blueBand.as[Int].?(2),
      "redGamma".as[Double].?, "greenGamma".as[Double].?, "blueGamma".as[Double].?,
      "contrast".as[Double].?, "brightness".as[Int].?,
      'alpha.as[Double].?, 'beta.as[Double].?,
      "min".as[Int].?, "max".as[Int].?,
      'equalize.as[Boolean].?(false)
    ).as(ImageParams.apply _)
}