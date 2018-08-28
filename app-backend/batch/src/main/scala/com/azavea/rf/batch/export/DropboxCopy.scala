package com.azavea.rf.batch.export

import java.io.InputStream
import java.net.URI

import com.amazonaws.services.s3.model.{ListObjectsRequest, ObjectListing}
import com.azavea.rf.batch.Job
import com.azavea.rf.batch.util.S3
import com.dropbox.core.v2.files.{CreateFolderErrorException, WriteMode}

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.util._

final case class DropboxCopy(source: URI,
                             target: URI,
                             accessToken: String,
                             region: Option[String] = None)
    extends Job {
  val name: String = DropboxCopy.name

  lazy val s3Client = S3(region = region)

  def copyListing(is: (InputStream, String) => String): List[Future[String]] = {
    @tailrec
    def copy(listing: ObjectListing,
             accumulator: List[Future[String]]): List[Future[String]] = {
      def getObjects: List[Future[String]] =
        listing.getObjectSummaries.asScala.toList
          .filterNot(_.getKey.endsWith("/"))
          .map { os =>
            Future {
              val (bucket, key) = os.getBucketName -> os.getKey
              logger.info(s"Uploading: $key")
              val obj = s3Client.client.getObject(bucket, key)
              is(obj.getObjectContent, key)
            }
          } ::: accumulator

      if (!listing.isTruncated) getObjects
      else copy(s3Client.client.listNextBatchOfObjects(listing), getObjects)
    }

    val prefix = {
      val p = source.getPath.tail
      if (!p.endsWith("/")) s"$p/" else p
    }

    val listObjectsRequest =
      new ListObjectsRequest()
        .withBucketName(source.getHost)
        .withPrefix(prefix)
        .withDelimiter("/")

    copy(s3Client.client.listObjects(listObjectsRequest), Nil)
  }

  def run(): Unit = {
    logger.info(s"Dropbox copy from $source to $target Started...")
    val client = dropboxConfig.client(accessToken)

    try {
      client.files
        .createFolder(target.getPath)
    } catch {
      case e: CreateFolderErrorException =>
        logger.warn(s"Target Path already exists, ${e.errorValue}")
    }

    Future.sequence(
      copyListing({
        case (is, key) =>
          try {
            logger.info(s"${key}".trim)
            logger.info(s"${target.getPath}/${key.split("/").last}")

            client.files
              .uploadBuilder(s"${target.getPath}/${key.split("/").last}")
              .withMode(WriteMode.OVERWRITE)
              .uploadAndFinish(is)
              .getId
              .split("id:")
              .last
          } finally is.close()
      })
    ) onComplete {
      case Success(list) => {
        logger.info(s"Dropbox copy finished with IDs: ${list.mkString(", ")}")
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
      case List(source, target, accessToken, targetRegion) =>
        DropboxCopy(new URI(source),
                    new URI(target),
                    accessToken,
                    Some(targetRegion))
      case List(source, target, accessToken) =>
        DropboxCopy(new URI(source), new URI(target), accessToken)
      case list =>
        throw new IllegalArgumentException(
          s"Arguments could not be parsed: $list")
    }

    job.run
  }
}
