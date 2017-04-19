package com.azavea.rf.batch

import com.azavea.rf.batch.util.conf.Config
import geotrellis.util.LazyLogging

trait Job extends Config with LazyLogging {
  val name: String
}
