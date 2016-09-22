package com.azavea.rf

import com.azavea.rf.utils.PaginatedResponse
import com.azavea.rf.datamodel.latest.schema.tables.{BucketsRow}


package object bucket extends RfJsonProtocols {

  implicit val bucketsRowFormat = jsonFormat11(BucketsRow)
  implicit val createBucketFormat = jsonFormat5(CreateBucket)
  implicit val paginatedBucketFormat = jsonFormat6(PaginatedResponse[BucketsRow])

}
