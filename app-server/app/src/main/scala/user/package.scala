package com.azavea.rf

import com.azavea.rf.datamodel._

/**
  * Json formats for user
  */
package object user extends RfJsonProtocols {
  implicit val userCreateFormat = jsonFormat3(UserCreate)
  implicit val organizationWithRoleFormat = jsonFormat3(OrganizationWithRole)
  implicit val usersRowWithOrgsFormat = jsonFormat2(UserWithOrgs)
  implicit val paginatedUserWithOrgsFormat = jsonFormat6(PaginatedResponse[UserWithOrgs])
}
