package com.azavea.rf.database

import com.azavea.rf.database.util.Filters
import com.azavea.rf.datamodel._


package object filters {

  implicit val aoiQueryParams = Filterable[AOI, AoiQueryParameters] { qp: AoiQueryParameters =>
      Filters.organization(qp.orgParams) ++
      Filters.user(qp.userParams) ++
      Filters.timestamp(qp.timestampParams)
  }

  implicit val aoiUser = Filterable[AOI, User] { user: User =>
    Filters.filterToSharedIfNotInRoot(user)
  }

}
