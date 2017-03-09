package com.azavea.rf.common

import net.spy.memcached._
import com.typesafe.scalalogging.LazyLogging

package object cache extends LazyLogging {
  implicit class withMemcachedClientMethods(client: MemcachedClient)
    extends MemcachedClientMethods(client)
}
