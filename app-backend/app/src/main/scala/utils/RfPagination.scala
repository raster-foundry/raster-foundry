package com.azavea.rf.utils

import com.azavea.rf.datamodel.PageRequest

object RfPagination{
  def validatePage(page: PageRequest) : PageRequest = {
    if (page.offset > -1) {
      page
    } else {
      throw new IllegalStateException(
        s"Error fetching scene list: page parameter is invalid or not present"
      )
    }
  }
}
