package com.rasterfoundry.api.tool

import com.rasterfoundry.common.datamodel._
import com.rasterfoundry.api.utils.queryparams._

trait ToolQueryParameterDirective extends QueryParametersCommon {
  def combinedToolQueryParams =
    (
      orgQueryParams &
        userQueryParameters &
        timestampQueryParameters &
        searchParams &
        ownershipTypeQueryParameters &
        groupQueryParameters
    ).as(CombinedToolQueryParameters.apply _)
}
