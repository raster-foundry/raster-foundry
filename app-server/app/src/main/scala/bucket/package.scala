package com.azavea.rf

import com.azavea.rf.datamodel._

package object bucket extends RfJsonProtocols {

  implicit val bucketsRowFormat = jsonFormat11(Bucket)
  implicit val createBucketFormat = jsonFormat5(CreateBucket)
  implicit val paginatedBucketFormat = jsonFormat6(PaginatedResponse[Bucket])

}
