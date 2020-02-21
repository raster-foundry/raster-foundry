package com.rasterfoundry.api.organization

import com.rasterfoundry.api.utils.queryparams._
import com.rasterfoundry.datamodel._

trait OrganizationQueryParameterDirective extends QueryParametersCommon {

  def organizationQueryParameters =
    (
      timestampQueryParameters &
        searchParams &
        activationParams &
        platformIdParams
    ).as(OrganizationQueryParameters.apply _)

}
