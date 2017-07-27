package com.azavea.rf.common.cache.kryo

import com.azavea.rf.common.Config
import net.spy.memcached._
import net.spy.memcached.transcoders.Transcoder

/** Extends Memcached client configuration object to provide custom (kryo) transcoder. */
class KryoConnectionFactory extends DefaultConnectionFactory(ClientMode.Static) {
  override def getDefaultTranscoder: Transcoder[AnyRef] = {
    new KryoTranscoder
  }

  override def getOperationTimeout: Long = Config.memcached.timeout
}
