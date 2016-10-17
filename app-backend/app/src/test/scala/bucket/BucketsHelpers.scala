package com.azavea.rf.bucket

import java.util.UUID
import com.azavea.rf.datamodel._
import com.azavea.rf.AuthUtils
import java.sql.Timestamp
import java.time.Instant


trait BucketSpecHelper {
  val authorization = AuthUtils.generateAuthHeader("Default")
  val baseBucket = "/api/buckets/"
  val publicOrgId = UUID.fromString("dfac6307-b5ef-43f7-beda-b9f208bb7726")
  val fakeOrgId = UUID.fromString("dfac6307-b5ef-43f7-beda-b9f208bb7725")

  val newBucket1 = Bucket.Create(
    publicOrgId, "Test One", "This is the first test bucket", Visibility.Public, List("testing")
  )

  val newBucket2 = Bucket.Create(
    publicOrgId, "Test Two", "This is the second test bucket", Visibility.Public, List("testing")
  )

  val newScene = Scene.Create(
    publicOrgId, 0, Visibility.Public, 20.2f, List("Test", "Public", "Low Resolution"), "TEST_ORG",
    Map("instrument type" -> "satellite", "splines reticulated" -> 0):Map[String, Any], None,
    Some(Timestamp.from(Instant.parse("2016-09-19T14:41:58.408544Z"))),
    JobStatus.Processing, JobStatus.Processing, JobStatus.Processing, None, None, "test scene bucket",
    List.empty[Image.Identified], None, List.empty[Thumbnail.Identified]
  )

}
