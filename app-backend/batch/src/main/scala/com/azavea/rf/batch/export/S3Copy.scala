package com.azavea.rf.batch.export

import java.net.URI

import com.azavea.rf.batch.Job
import com.azavea.rf.batch.util.S3

final case class S3Copy(source: URI, target: URI, region: Option[String] = None)
    extends Job {
  val name = S3Copy.name

  lazy val client = S3(region = region)

  @SuppressWarnings(Array("CatchThrowable")) // need to ensure actor system stops and exit status set
  def run(): Unit =
    try {
      logger.info(s"S3 copy from $source to $target started...")
      client.copyListing(
        source.getHost,
        target.getHost,
        source.getPath.tail,
        target.getPath.tail,
        client.listObjects(source.getHost, source.getPath.tail)
      )
      logger.info("S3 copy finished")
    } catch {
      case e: Throwable => {
        sendError(e)
        e.printStackTrace()
        System.exit(1)
      }
    }
}

object S3Copy {
  val name = "s3_copy"

  def main(args: Array[String]): Unit = {
    val job = args.toList match {
      case List(source, target, targetRegion) =>
        S3Copy(new URI(source), new URI(target), Some(targetRegion))
      case List(source, target) => S3Copy(new URI(source), new URI(target))
      case list =>
        throw new IllegalArgumentException(
          s"Arguments could not be parsed: $list")
    }

    job.run
  }
}
