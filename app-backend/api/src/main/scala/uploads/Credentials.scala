package com.azavea.rf.api.uploads

import java.sql.Timestamp
import java.util.{Date, UUID}

import com.azavea.rf.api.utils.Config
import com.azavea.rf.datamodel.User
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.amazonaws.auth.{AWSCredentials, AWSSessionCredentials, AWSStaticCredentialsProvider}
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder
import com.amazonaws.services.securitytoken.model.AssumeRoleWithWebIdentityRequest
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import io.circe.generic.JsonCodec
import io.circe.optics.JsonPath._
import io.circe.Json
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._


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

object CredentialsService extends Config with LazyLogging {
  import com.azavea.rf.api.AkkaSystem._

  def getCredentials(user: User, uploadId: UUID, jwt: String): CredentialsWithBucketPath = {
    val path = s"user-uploads/${user.id}/${uploadId.toString}"
    val stsClient = AWSSecurityTokenServiceClientBuilder.defaultClient
    val stsRequest = new AssumeRoleWithWebIdentityRequest

    stsRequest.setRoleArn(scopedUploadRoleArn)
    stsRequest.setRoleSessionName(s"upload${uploadId}")
    stsRequest.setWebIdentityToken(jwt)
    // Sessions for AWS account owners are restricted to a maximum of 3600 seconds. Longer durations seemed to give 501.
    // https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/securitytoken/model/GetSessionTokenRequest.html
    stsRequest.setDurationSeconds(3600)

    val stsCredentials = stsClient.assumeRoleWithWebIdentity(stsRequest).getCredentials

    val credentials = Credentials(
      stsCredentials.getAccessKeyId,
      stsCredentials.getExpiration.toString,
      stsCredentials.getSecretAccessKey,
      stsCredentials.getSessionToken
    )

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
}
