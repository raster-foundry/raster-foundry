package com.rasterfoundry.api.uploads

import com.rasterfoundry.api.utils.Config
import com.rasterfoundry.common.S3
import com.rasterfoundry.datamodel.Upload

import com.amazonaws.auth.{AWSCredentials, AWSSessionCredentials}
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder
import com.amazonaws.services.securitytoken.model.AssumeRoleWithWebIdentityRequest
import com.typesafe.scalalogging.LazyLogging
import io.circe.generic.JsonCodec

import java.sql.Timestamp
import java.util.Date

@JsonCodec
final case class Credentials(
    AccessKeyId: String,
    Expiration: String,
    SecretAccessKey: String,
    SessionToken: String
) extends AWSCredentials
    with AWSSessionCredentials {
  override def getAWSAccessKeyId: String = this.AccessKeyId

  override def getAWSSecretKey: String = this.SecretAccessKey

  override def getSessionToken: String = this.SessionToken
}

@JsonCodec
final case class CredentialsWithBucketPath(
    credentials: Credentials,
    bucketPath: String
)

object CredentialsService extends Config with LazyLogging {

  def getCredentials(upload: Upload, jwt: String): CredentialsWithBucketPath = {
    val path = upload.s3Path
    val stsClient = AWSSecurityTokenServiceClientBuilder.defaultClient
    val stsRequest = new AssumeRoleWithWebIdentityRequest

    stsRequest.setRoleArn(scopedUploadRoleArn)
    stsRequest.setRoleSessionName(s"upload${upload.id}")
    stsRequest.setWebIdentityToken(jwt)
    // Sessions for AWS account owners are restricted to a maximum of 3600 seconds. Longer durations seemed to give 501.
    // https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/securitytoken/model/GetSessionTokenRequest.html
    stsRequest.setDurationSeconds(3600)

    val stsCredentials =
      stsClient.assumeRoleWithWebIdentity(stsRequest).getCredentials

    val credentials = Credentials(
      stsCredentials.getAccessKeyId,
      stsCredentials.getExpiration.toString,
      stsCredentials.getSecretAccessKey,
      stsCredentials.getSessionToken
    )

    val s3Client = S3()

    // Add timestamp object to test credentials
    val now = new Timestamp(new Date().getTime)
    s3Client.putObjectString(
      dataBucket,
      s"${path}/RFUploadAccessTestFile",
      s"Allow Upload Access for RF: ${path} at ${now.toString}"
    )

    val bucketUrl = s3Client.getS3Url(dataBucket, path)

    CredentialsWithBucketPath(credentials, bucketUrl.toString)
  }
}
