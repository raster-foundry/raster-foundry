package com.azavea.rf

import com.azavea.rf.datamodel._

package object bucket extends RfJsonProtocols {
  implicit val paginatedBucketFormat = jsonFormat6(PaginatedResponse[Bucket])
}
