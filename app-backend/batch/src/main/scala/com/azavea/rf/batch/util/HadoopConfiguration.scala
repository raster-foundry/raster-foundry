package com.azavea.rf.batch.util

import java.io.{ObjectInputStream, ObjectOutputStream}

import org.apache.hadoop.conf.Configuration

/**
  * Serializable [[Configuration]] wrapper
  * @param conf Hadoop Configuration
  */
final case class HadoopConfiguration(var conf: Configuration)
    extends Serializable {
  def get: Configuration = conf

  private def writeObject(out: ObjectOutputStream): Unit =
    conf.write(out)

  private def readObject(in: ObjectInputStream): Unit = {
    conf = new Configuration()
    conf.readFields(in)
  }

  private def readObjectNoData(): Unit =
    conf = new Configuration()
}
