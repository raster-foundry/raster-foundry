package com.azavea.rf.tile.image

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._

object ColorCorrectParams {
  def colorCorrectParams: Directive1[ColorCorrect.Params] =
    parameters(
      'redBand.as[Int].?(0), 'greenBand.as[Int].?(1), 'blueBand.as[Int].?(2),
      "redGamma".as[Double].?, "greenGamma".as[Double].?, "blueGamma".as[Double].?,
      "contrast".as[Double].?, "brightness".as[Int].?,
      'alpha.as[Double].?, 'beta.as[Double].?,
      "min".as[Int].?, "max".as[Int].?,
      'equalize.as[Boolean].?(false)
    ).as(ColorCorrect.Params.apply _)
}