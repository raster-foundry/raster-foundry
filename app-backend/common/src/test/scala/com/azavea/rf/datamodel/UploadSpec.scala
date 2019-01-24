package com.rasterfoundry.common.datamodel

import io.circe.syntax._
import org.scalatest._

import java.util.UUID

class UploadTestSuite extends FunSuite with Matchers {

  test(
    "non-platform admins should not be able to create uploads for other users") {
    val uploadCreate = Upload.Create(
      UploadStatus.Uploaded,
      FileType.Geotiff,
      UploadType.Dropbox,
      List.empty,
      UUID.randomUUID,
      ().asJson,
      Some("foo"), // proposed owner
      Visibility.Private,
      None,
      None,
      None
    )
    // note the id is not "foo"
    val user = User.Create("bar").toUser
    val platformId = UUID.randomUUID
    an[IllegalArgumentException] should be thrownBy (
      uploadCreate.toUpload(user,
                            (platformId, false),
                            Some(platformId))
    )
  }

  test(
    "platform admins should be able to create uploads for other users in the same platform") {
    val uploadCreate = Upload.Create(
      UploadStatus.Uploaded,
      FileType.Geotiff,
      UploadType.Dropbox,
      List.empty,
      UUID.randomUUID,
      ().asJson,
      Some("foo"), // proposed owner
      Visibility.Private,
      None,
      None,
      None
    )
    // note the id is not "foo"
    val user = User.Create("bar").toUser
    val platformId = UUID.randomUUID
    Some(
      uploadCreate
        .toUpload(user, (platformId, true), Some(platformId))
        .owner) shouldEqual uploadCreate.owner
  }

  test(
    "platform admins should not be able to create uploads for other users in different platforms") {
    val uploadCreate = Upload.Create(
      UploadStatus.Uploaded,
      FileType.Geotiff,
      UploadType.Dropbox,
      List.empty,
      UUID.randomUUID,
      ().asJson,
      Some("foo"), // proposed owner
      Visibility.Private,
      None,
      None,
      None
    )
    // note the id is not "foo"
    val user = User.Create("bar").toUser
    an[IllegalArgumentException] should be thrownBy (
      uploadCreate.toUpload(user,
                            (UUID.randomUUID, false),
                            Some(UUID.randomUUID))
    )
  }

  test(
    "superusers should also be able to create uploads for other users regardless of platform") {
    val uploadCreate = Upload.Create(
      UploadStatus.Uploaded,
      FileType.Geotiff,
      UploadType.Dropbox,
      List.empty,
      UUID.randomUUID,
      ().asJson,
      Some("foo"), // proposed owner
      Visibility.Private,
      None,
      None,
      None
    )
    // note the id is not "foo"
    val user = User
      .Create("bar")
      .toUser
      .copy(
        isSuperuser = true
      )
    Some(
      uploadCreate
        .toUpload(user, (UUID.randomUUID, true), Some(UUID.randomUUID))
        .owner) shouldEqual uploadCreate.owner
  }
}
