package com.rasterfoundry.datamodel

import io.circe.syntax._
import org.scalatest._

import java.util.UUID

class UploadTestSuite extends FunSuite with Matchers {

  test(
    "non-platform admins should not be able to create uploads for other users"
  ) {
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
      None,
      None,
      None,
      false
    )
    // note the id is not "foo"
    val user = User.Create("bar").toUser
    val platformId = UUID.randomUUID
    an[IllegalArgumentException] should be thrownBy (
      uploadCreate.toUpload(
        user,
        (platformId, false),
        Some(platformId),
        0
      )
    )
  }

  test(
    "platform admins should be able to create uploads for other users in the same platform"
  ) {
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
      None,
      None,
      None,
      false
    )
    // note the id is not "foo"
    val user = User.Create("bar").toUser
    val platformId = UUID.randomUUID
    Some(
      uploadCreate
        .toUpload(user, (platformId, true), Some(platformId), 0)
        .owner
    ) shouldEqual uploadCreate.owner
  }

  test(
    "platform admins should not be able to create uploads for other users in different platforms"
  ) {
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
      None,
      None,
      None,
      false
    )
    // note the id is not "foo"
    val user = User.Create("bar").toUser
    an[IllegalArgumentException] should be thrownBy (
      uploadCreate.toUpload(
        user,
        (UUID.randomUUID, false),
        Some(UUID.randomUUID),
        0
      )
    )
  }

  test(
    "superusers should also be able to create uploads for other users regardless of platform"
  ) {
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
      None,
      None,
      None,
      false
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
        .toUpload(user, (UUID.randomUUID, true), Some(UUID.randomUUID), 0)
        .owner
    ) shouldEqual uploadCreate.owner
  }
}
