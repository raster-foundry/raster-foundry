package com.azavea.rf.common.cache.kryo

import net.spy.memcached.transcoders.Transcoder
import net.spy.memcached._

import java.net.InetSocketAddress
import scala.collection.JavaConverters._

/** Extends the standard [net.spy.MemcachedClient] to syntactically sweeten client creation */
class KryoMemcachedClient(addrs: InetSocketAddress*)
    extends MemcachedClient(new KryoConnectionFactory, addrs.toList.asJava)

object KryoMemcachedClient {
  def apply(addrs: InetSocketAddress): KryoMemcachedClient =
    new KryoMemcachedClient(addrs)
}
