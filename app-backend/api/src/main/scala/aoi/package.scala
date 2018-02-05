package com.azavea.rf.api

import com.azavea.rf.datamodel._
import com.azavea.rf.database._
import com.azavea.rf.database.util.Filters


package object aoi {
  implicit val aoiQueryParams = Filterable[AOI, AoiQueryParameters] { qp: AoiQueryParameters =>
      Filters.organization(qp.orgParams) ++
      Filters.user(qp.userParams) ++
      Filters.timestamp(qp.timestampParams)
  }

  implicit val aoiUser = Filterable[AOI, User] { user: User =>
    Filters.filterToSharedIfNotInRoot(user)
  }

}

