package com.azavea.rf.api.scene

import com.azavea.rf.datamodel.SceneThumbnailQueryParameters

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters

trait ThumbnailQueryParameterDirective {
  val thumbnailQueryParameters = parameters(
    ('width.as[Int].?,
     'height.as[Int].?,
     'token.as[String],
     'red.as[Int].?,
     'green.as[Int].?,
     'blue.as[Int].?,
     'floor.as[Int].?)).as(SceneThumbnailQueryParameters.apply _)
}
