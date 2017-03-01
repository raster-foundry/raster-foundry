package com.azavea.rf.common.cache.kryo

import net.spy.memcached.transcoders.Transcoder
import net.spy.memcached._

/** Extends Memcached client configuration object to provide custom (kryo) transcoder. */
class KryoConnectionFactory extends DefaultConnectionFactory(ClientMode.Static) {
  override def getDefaultTranscoder: Transcoder[AnyRef] = {
    new KryoTranscoder
  }
}
