package com.azavea.rf

import com.azavea.rf.datamodel._

package object organization extends RfJsonProtocols {

  //implicit val organizationCreateFormat = jsonFormat1(OrganizationCreate)
  //implicit val userWithRoleCreateFormat = jsonFormat2(UserWithRoleCreate)
  //implicit val userWithRoleFormat = jsonFormat4(UserWithRole)
  implicit val paginatedOrganizationsFormat = jsonFormat6(PaginatedResponse[Organization])
  implicit val paginatedUserWithRoleFormat = jsonFormat6(PaginatedResponse[User.WithRole])
}
