package com.azavea.rf.api.workspace

import java.util.UUID

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters

import com.azavea.rf.datamodel._
import com.azavea.rf.api.utils.queryparams._

///** Trait to abstract out query parameters for workspaces */
trait WorkspaceQueryParametersDirective extends QueryParametersCommon {

  val workspaceSpecificQueryParams = parameters((
    'search.as[String].?
  )).as(WorkspaceQueryParameters.apply _)

  val workspaceQueryParameters = (
    userQueryParameters &
    timestampQueryParameters &
    workspaceSpecificQueryParams
  ).as(CombinedWorkspaceQueryParameters.apply _)
}
