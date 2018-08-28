package com.azavea.rf.common.cache.kryo

import com.twitter.chill.{KryoPool, ScalaKryoInstantiator}
import net.spy.memcached.CachedData
import net.spy.memcached.transcoders.Transcoder

/** The KryoTranscoder provides conversion between (kryo serialized) byte arrays and jvm objects */
class KryoTranscoder extends Transcoder[AnyRef] {
  val kryo: KryoPool = ScalaKryoInstantiator.defaultPool

  def asyncDecode(d: CachedData): Boolean = true

  def getMaxSize: Int = CachedData.MAX_SIZE

  def encode(obj: AnyRef): CachedData = {
    val bytes = kryo.toBytesWithClass(obj)
    new CachedData(0, bytes, CachedData.MAX_SIZE)
  }

  @SuppressWarnings(Array("MethodReturningAny"))
  def decode(d: CachedData): AnyRef = {
    kryo.fromBytes(d.getData)
  }
}
