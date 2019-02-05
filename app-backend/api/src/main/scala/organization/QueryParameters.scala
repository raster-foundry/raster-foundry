package com.rasterfoundry.api.organization

import com.rasterfoundry.common.datamodel._
import com.rasterfoundry.api.utils.queryparams._

trait OrganizationQueryParameterDirective extends QueryParametersCommon {

  def organizationQueryParameters =
    (
      timestampQueryParameters &
        searchParams &
        activationParams &
        platformIdParams
    ).as(OrganizationQueryParameters.apply _)

}
