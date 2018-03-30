package com.azavea.rf.api.template

import java.util.UUID

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters

import com.azavea.rf.datamodel._
import com.azavea.rf.api.image.ImageQueryParametersDirective
import com.azavea.rf.api.utils.queryparams._

///** Trait to abstract out query parameters for templates */
trait TemplateQueryParametersDirective extends QueryParametersCommon {

  val templateSpecificQueryParams = parameters((
    'category.as[String].*,
    'tag.as[String].*,
    'name.as[String].?
  )).as(TemplateQueryParameters.apply _)

  val templateQueryParameters = (orgQueryParams &
    userQueryParameters &
    timestampQueryParameters &
    templateSpecificQueryParams
  ).as(CombinedTemplateQueryParameters.apply _)
}
