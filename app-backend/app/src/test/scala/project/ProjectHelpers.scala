package com.azavea.rf.project

import java.util.UUID
import com.azavea.rf.datamodel._
import com.azavea.rf.AuthUtils
import java.sql.Timestamp
import java.time.Instant


trait ProjectSpecHelper {
  val authorization = AuthUtils.generateAuthHeader("Default")
  val baseProject = "/api/projects/"
  val publicOrgId = UUID.fromString("dfac6307-b5ef-43f7-beda-b9f208bb7726")
  val fakeOrgId = UUID.fromString("dfac6307-b5ef-43f7-beda-b9f208bb7725")

  val newProject1 = Project.Create(
    publicOrgId, "Test One", "This is the first test project", Visibility.Public, List("testing")
  )

  val newProject2 = Project.Create(
    publicOrgId, "Test Two", "This is the second test project", Visibility.Public, List("testing")
  )

  val newScene = Scene.Create(
    None, publicOrgId, 0, Visibility.Public, List("Test", "Public", "Low Resolution"), "TEST_ORG",
    Map("instrument type" -> "satellite", "splines reticulated" -> 0):Map[String, Any], None,
    Some(Timestamp.from(Instant.parse("2016-09-19T14:41:58.408544Z"))),
    JobStatus.Processing, JobStatus.Processing, JobStatus.Processing, None, None, "test scene project",
    None, List.empty[String], List.empty[Image.Banded], List.empty[Thumbnail.Identified]
  )

}
