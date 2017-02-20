package com.azavea.rf.common.cache.kryo

import net.spy.memcached.transcoders.Transcoder
import net.spy.memcached._

import scala.collection.JavaConverters._
import java.net.InetSocketAddress

/** Extends Memcached client configuration object to provide custom (kryo) transcoder. */
class KryoConnectionFactory extends DefaultConnectionFactory(ClientMode.Static) {
  override def getDefaultTranscoder: Transcoder[AnyRef] = new KryoTranscoder()
}

