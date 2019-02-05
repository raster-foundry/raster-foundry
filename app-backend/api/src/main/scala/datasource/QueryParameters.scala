package com.rasterfoundry.api.datasource

import com.rasterfoundry.common.datamodel._
import com.rasterfoundry.api.utils.queryparams._

trait DatasourceQueryParameterDirective extends QueryParametersCommon {
  def datasourceQueryParams =
    (
      userQueryParameters &
        searchParams &
        ownershipTypeQueryParameters &
        groupQueryParameters
    ).as(DatasourceQueryParameters.apply _)
}
