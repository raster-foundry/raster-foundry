
package com.azavea.rf

import com.azavea.rf.datamodel._

package object datasource extends RfJsonProtocols {

  //implicit val organizationCreateFormat = jsonFormat1(OrganizationCreate)
  //implicit val userWithRoleCreateFormat = jsonFormat2(UserWithRoleCreate)
  //implicit val userWithRoleFormat = jsonFormat4(UserWithRole)
  implicit val paginatedDatasourceFormat = jsonFormat6(PaginatedResponse[Datasource])
}
