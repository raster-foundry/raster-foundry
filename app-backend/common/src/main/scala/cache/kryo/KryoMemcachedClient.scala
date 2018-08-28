package com.azavea.rf.common.cache.kryo

import net.spy.memcached._
import java.net.InetSocketAddress
import com.azavea.rf.common.Config
import scala.collection.JavaConverters._

/** Extends the standard [net.spy.MemcachedClient] to syntactically sweeten client creation */
class KryoMemcachedClient(addrs: InetSocketAddress*)
    extends MemcachedClient(new KryoConnectionFactory, addrs.toList.asJava)

object KryoMemcachedClient {
  def apply(addrs: InetSocketAddress): KryoMemcachedClient =
    new KryoMemcachedClient(addrs)

  def default: KryoMemcachedClient =
    KryoMemcachedClient(
      new InetSocketAddress(Config.memcached.host, Config.memcached.port))
}
