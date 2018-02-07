package com.azavea.rf

import com.azavea.rf.database.meta.RFMeta
import com.azavea.rf.database.filter.Filterables

package object database {
  object Implicits extends RFMeta with Filterables
}
