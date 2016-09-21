package com.azavea.rf.bucket

import com.azavea.rf.utils.queryparams._


/** Case class for bucket query parameters */
case class BucketQueryParameters(
  orgParams: OrgQueryParameters,
  userParams: UserQueryParameters,
  timestampParams: TimestampQueryParameters
)


/** Query parameters for buckets */
trait BucketQueryParameterDirective extends QueryParametersCommon {

  val bucketQueryParameters = (orgQueryParams &
    userQueryParameters &
    timestampQueryParameters).as(BucketQueryParameters)

}
