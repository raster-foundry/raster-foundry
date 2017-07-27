package com.azavea.rf.common.cache.kryo

import com.azavea.rf.common.Config
import net.spy.memcached._
import net.spy.memcached.transcoders.Transcoder

/** Extends Memcached connection factory configuration object to provide custom configuration. */
class KryoConnectionFactory extends DefaultConnectionFactory() {
  override def getClientMode: ClientMode = Config.memcached.clientMode

  override def getDefaultTranscoder: Transcoder[AnyRef] = {
    new KryoTranscoder
  }

  override def getOperationTimeout: Long = Config.memcached.timeout
}
