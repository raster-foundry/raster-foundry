package com.azavea.rf.common

import com.typesafe.scalalogging.LazyLogging
import net.spy.memcached._

package object cache extends LazyLogging {

  implicit class WithMemcachedClientMethods(client: MemcachedClient)
      extends MemcachedClientMethods(client)

}
