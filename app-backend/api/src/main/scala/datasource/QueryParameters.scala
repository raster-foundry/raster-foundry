package com.rasterfoundry.api.datasource

import com.rasterfoundry.api.utils.queryparams._
import com.rasterfoundry.datamodel._

trait DatasourceQueryParameterDirective extends QueryParametersCommon {
  def datasourceQueryParams =
    (
      userQueryParameters &
        searchParams &
        ownershipTypeQueryParameters &
        groupQueryParameters
    ).as(DatasourceQueryParameters.apply _)
}
