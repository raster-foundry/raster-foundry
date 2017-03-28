package com.azavea.rf.common

import java.sql.Timestamp

import com.amazonaws.regions.Regions
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder
import com.amazonaws.services.securitytoken.model.{GetFederationTokenRequest, Credentials => STSCredentials}

package object STS {

  case class Credentials (
    AccessKeyId: String,
    SecretAccessKey: String,
    SessionToken: String,
    Expiration: Timestamp
  )

  def toCredentials(result: STSCredentials): Credentials =
    Credentials(
      result.getAccessKeyId,
      result.getSecretAccessKey,
      result.getSessionToken,
      new Timestamp(result.getExpiration.getTime)
    )

  def getCredentialsForUpload(uploadId: String, bucket: String): Credentials =
    getCredentialsForUpload(uploadId, bucket, "user-uploads")

  def getCredentialsForUpload(uploadId: String, bucket: String, path: String): Credentials =
    getCredentialsForUpload(uploadId, bucket, path, Regions.US_EAST_1)

  def getCredentialsForUpload(uploadId: String, bucket: String, path: String, regions: Regions): Credentials = {
    val client = AWSSecurityTokenServiceClientBuilder.standard.withRegion(regions).build

    // Federation user's name must be less than or equal to 32 characters.
    // We set it to the uploadId uuid without hyphens, which is exactly 32 characters.
    // We also cap it to 32 to be safe.
    val userName = uploadId.filter(_ != '-').take(32)

    // We give the Federation user ability to list objects within their folder,
    // and upload, download, and delete objects within their folder.
    // Policy must be less than or equal to 2048 bytes, so we remove all whitespace.
    val policy =
      s"""
         |{
         |  "Version": "2012-10-17",
         |  "Statement": [
         |    {
         |      "Effect": "Allow",
         |      "Action": [
         |        "s3:ListBucket"
         |      ],
         |      "Resource": [
         |        "arn:aws:s3:::${bucket}"
         |      ],
         |      "Condition": {
         |        "StringLike": {
         |          "s3:prefix": [
         |            "${path}/${uploadId}/*"
         |          ]
         |        }
         |      }
         |    },
         |    {
         |      "Effect": "Allow",
         |      "Action": [
         |        "s3:GetObject",
         |        "s3:PutObject",
         |        "s3:DeleteObject"
         |      ],
         |      "Resource": [
         |        "arn:aws:s3:::${bucket}/${path}/${uploadId}/*"
         |      ]
         |    }
         |  ]
         |}
       """.stripMargin.filter(_ > ' ')

    val request = new GetFederationTokenRequest(userName).withPolicy(policy)
    val response = client.getFederationToken(request)
    toCredentials(response.getCredentials)
  }
}
