package com.azavea.rf

import com.azavea.rf.utils.PaginatedResponse
import com.azavea.rf.datamodel.latest.schema.tables.OrganizationsRow

package object organization extends RfJsonProtocols {

  implicit val organizationsRowCreateFormat = jsonFormat1(OrganizationsRowCreate)
  implicit val paginatedOrganizationsFormat = jsonFormat6(PaginatedResponse[OrganizationsRow])
  implicit val userWithRoleCreateFormat = jsonFormat2(UserWithRoleCreate)
  implicit val userWithRoleFormat = jsonFormat4(UserWithRole)
  implicit val paginatedUserWithRoleFormat = jsonFormat6(PaginatedResponse[UserWithRole])
}
