package com.azavea.rf.common.cache.kryo

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.{Input, Output}
import net.spy.memcached.CachedData
import net.spy.memcached.transcoders.Transcoder


/** The KryoTranscoder provides conversion between (kryo serialized) byte arrays and jvm objects */
class KryoTranscoder extends Transcoder[AnyRef] {
  import KryoTranscoder._

  def asyncDecode(d: CachedData): Boolean = true

  def getMaxSize: Int = CachedData.MAX_SIZE

  def encode(o: AnyRef): CachedData = {
    val output = new Output(4096)
    kryo.writeObject(output, o)
    output.flush()
    val bytes = output.toBytes
    val cd = new CachedData(0, bytes, CachedData.MAX_SIZE)
    output.close()
    cd
  }

  def decode(d: CachedData): AnyRef = {
    val input = new Input(d.getData)
    val obj = kryo.readClassAndObject(input)
    input.close()
    obj
  }
}

object KryoTranscoder {
  val kryo = new Kryo()
}
