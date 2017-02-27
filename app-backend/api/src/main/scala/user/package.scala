package com.azavea.rf.api

import com.azavea.rf.datamodel._

/**
  * Json formats for user
  */
package object user extends RfJsonProtocols {

  implicit val paginatedUserWithOrgsFormat = jsonFormat6(PaginatedResponse[User.WithOrgs])

}
