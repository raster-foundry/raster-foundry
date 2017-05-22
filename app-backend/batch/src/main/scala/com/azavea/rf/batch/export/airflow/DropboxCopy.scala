package com.azavea.rf.batch.export.airflow

import com.amazonaws.services.s3.model.ObjectListing
import com.azavea.rf.batch.Job
import com.azavea.rf.batch.util.S3

import java.io.InputStream
import java.net.URI

import scala.collection.JavaConverters._
import scala.annotation.tailrec
import scala.concurrent.Future
import scala.util._

case class DropboxCopy(source: URI, target: URI, accessToken: String, region: Option[String] = None) extends Job {
  val name: String = DropboxCopy.name

  lazy val s3Client = S3(region = region)

  final def copyListing(is: (InputStream, String) => String): List[Future[String]] = {
    @tailrec
    def copy(listing: ObjectListing, accumulator: List[Future[String]]): List[Future[String]] = {
      if(!listing.isTruncated) accumulator
      else {
        copy(
          s3Client.client.listNextBatchOfObjects(listing),
          listing.getObjectSummaries.asScala.toList.map { os => Future {
            val (bucket, key) = os.getBucketName -> os.getKey
            val obj = s3Client.client.getObject(bucket, key)
            is(obj.getObjectContent, key)
          } } ::: accumulator
        )
      }
    }

    copy(s3Client.client.listObjects(source.getHost, source.getPath.tail), Nil)
  }

  def run: Unit = {
    logger.info(s"Dropbox copy from $source to $target Started...")

    val client = dropboxConfig.client(accessToken)
    Future.sequence(
      copyListing({ case (is, key) => try {
        client
          .files
          .uploadBuilder(s"${target.getPath}/${key}")
          .uploadAndFinish(is).getId
      } finally is.close() })
    ) onComplete {
      case Success(list) => {
        logger.info(s"Dropbox copy finished with IDs: ${list.mkString(",")}")
        stop
      }
      case Failure(e) => {
        sendError(e)
        e.printStackTrace()
        stop
        System.exit(1)
      }
    }
  }
}

object DropboxCopy {
  val name = "dropbox-copy"

  def main(args: Array[String]): Unit = {
    val job = args.toList match {
      case List(source, target, accessToken, targetRegion) => DropboxCopy(new URI(source), new URI(target), accessToken, Some(targetRegion))
      case List(source, target, accessToken) => DropboxCopy(new URI(source), new URI(target), accessToken)
      case list =>
        throw new IllegalArgumentException(s"Arguments could not be parsed: $list")
    }

    job.run
  }
}
