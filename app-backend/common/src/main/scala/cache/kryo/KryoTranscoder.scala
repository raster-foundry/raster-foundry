package com.azavea.rf.common.cache.kryo

import net.spy.memcached.CachedData
import net.spy.memcached.transcoders.Transcoder
import com.twitter.chill.ScalaKryoInstantiator

/** The KryoTranscoder provides conversion between (kryo serialized) byte arrays and jvm objects */
class KryoTranscoder extends Transcoder[AnyRef] {
  val kryo = ScalaKryoInstantiator.defaultPool

  def asyncDecode(d: CachedData): Boolean = true

  def getMaxSize: Int = CachedData.MAX_SIZE

  def encode(obj: AnyRef): CachedData = {
    kryo.toBytesWithClass()
    val bytes = kryo.toBytesWithClass(obj)
    new CachedData(0, bytes, CachedData.MAX_SIZE)
  }

  def decode(d: CachedData): AnyRef = {
    kryo.fromBytes(d.getData)
  }
}

