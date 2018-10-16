package com.rasterfoundry

import com.rasterfoundry.database.meta.RFMeta
import com.rasterfoundry.database.filter.Filterables

package object database {
  object Implicits extends RFMeta with Filterables
}
