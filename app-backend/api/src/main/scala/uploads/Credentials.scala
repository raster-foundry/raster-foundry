package com.azavea.rf.api.uploads

import java.sql.Timestamp
import java.util.{Date, UUID}

import com.azavea.rf.api.utils.{Auth0Exception, Config}
import com.azavea.rf.datamodel.User
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.amazonaws.auth.{AWSCredentials, AWSSessionCredentials, AWSStaticCredentialsProvider}
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import io.circe.generic.JsonCodec
import io.circe.optics.JsonPath._
import io.circe.Json
import de.heikoseeberger.akkahttpcirce.CirceSupport._


@JsonCodec
case class Credentials (
  AccessKeyId: String,
  Expiration: String,
  SecretAccessKey: String,
  SessionToken: String
) extends AWSCredentials with AWSSessionCredentials {
  override def getAWSAccessKeyId = this.AccessKeyId
  override def getAWSSecretKey = this.SecretAccessKey
  override def getSessionToken = this.SessionToken
}

@JsonCodec
case class CredentialsWithBucketPath (
  credentials: Credentials,
  bucketPath: String
)

object Auth0DelegationService extends Config with LazyLogging {
  import com.azavea.rf.api.AkkaSystem._

  val uri = Uri(s"https://$auth0Domain/delegation")

  // The AWS Delegation response has many things, most of which we don't care
  // about. We only want the "Credentials" key, so we setup this path to
  // optionally retrieve it from Json response.
  private val credentialsPath = root.Credentials.as[Credentials]

  def getCredentials(user: User, uploadId: UUID, jwt: String): Future[CredentialsWithBucketPath] = {
    val params = FormData(
      "api-type" -> "aws",
      "client_id" -> auth0ClientId,
      "grant_type" -> "urn:ietf:params:oauth:grant-type:jwt-bearer",
      "id_token" -> jwt,
      "target" -> auth0ClientId
    ).toEntity
    val path = s"user-uploads/${user.id}/${uploadId.toString}"

    Http()
      .singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = uri,
        entity = params
      ))
      .flatMap {
        case HttpResponse(StatusCodes.OK, _, entity, _) =>
          Unmarshal(entity).to[Json].map(credentialsPath.getOption).map {
            case Some(credentials) => {
              val s3 = AmazonS3ClientBuilder.standard
                         .withCredentials(new AWSStaticCredentialsProvider(credentials))
                         .withRegion(region)
                         .build()

              // Add timestamp object to test credentials
              val now = new Timestamp(new Date().getTime)
              s3.putObject(
                dataBucket,
                s"${path}/RFUploadAccessTestFile",
                s"Allow Upload Access for RF: ${path} at ${now.toString}"
              )

              val bucketUrl = s3.getUrl(dataBucket, path)

              CredentialsWithBucketPath(credentials, bucketUrl.toString)
            }
            case None => throw new Auth0Exception(
              StatusCodes.InternalServerError,
              "Missing credentials in AWS STS delegation response"
            )
          }
        case HttpResponse(errCode, _, err, _) =>
          throw new Auth0Exception(errCode, err.toString)
      }
  }
}
