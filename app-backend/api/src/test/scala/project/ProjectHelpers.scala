package com.azavea.rf.api.project

import com.azavea.rf.datamodel._
import com.azavea.rf.api.AuthUtils
import io.circe.syntax._

import geotrellis.vector.{MultiPolygon, Polygon, Point}
import geotrellis.slick.Projected

import java.util.UUID
import java.sql.Timestamp
import java.time.Instant



trait ProjectSpecHelper {
  val authorization = AuthUtils.generateAuthHeader("Default")
  val baseProject = "/api/projects/"
  val publicOrgId = UUID.fromString("dfac6307-b5ef-43f7-beda-b9f208bb7726")
  val fakeOrgId = UUID.fromString("dfac6307-b5ef-43f7-beda-b9f208bb7725")

  val newProject1 = Project.Create(
    publicOrgId, "Test One", "This is the first test project", Visibility.Public,
    Visibility.Private, false, Project.DEFAULT_CADENCE, None, List("testing"), false, None
  )

  val newProject2 = Project.Create(
    publicOrgId, "Test Two", "This is the second test project", Visibility.Public,
    Visibility.Private, false, Project.DEFAULT_CADENCE, None, List("testing"), false, None
  )

  val newProject3 = Project.Create(
    publicOrgId, "Test Three", "This is the third test project", Visibility.Public,
    Visibility.Private, false, Project.DEFAULT_CADENCE, None, List("testing"), false, None
  )

  val newProject4 = Project.Create(
    publicOrgId, "Test Three", "This is the third test project", Visibility.Private,
    Visibility.Private, false, Project.DEFAULT_CADENCE, None, List("testing"), false, None
  )

  val landsatId = UUID.fromString("697a0b91-b7a8-446e-842c-97cda155554d")

  val mpoly = Some(
    Projected(
      MultiPolygon(Polygon(Seq(Point(125.6, 10.1), Point(125.7,10.1), Point(125.7,10.2),
                               Point(125.6,10.2), Point(125.6,10.1)))), 4326)
  )

  def newScene(name: String, cloudCover: Option[Float] = None) = Scene.Create(
    None, publicOrgId, 0, Visibility.Public, List("Test", "Public", "Low Resolution"), landsatId,
    Map("instrument type" -> "satellite", "splines reticulated" -> "0").asJson,
    name, None, mpoly, mpoly, List.empty[String], List.empty[Image.Banded],
    List.empty[Thumbnail.Identified], None,
    SceneFilterFields(cloudCover,
                      Some(Timestamp.from(Instant.parse("2016-09-19T14:41:58.408544Z"))),
                      None,
                      None),
    SceneStatusFields(JobStatus.Processing, JobStatus.Processing, IngestStatus.NotIngested)
  )

  def newPrivateScene(name: String, cloudCover: Option[Float] = None) = Scene.Create(
    None, publicOrgId, 0, Visibility.Private, List("Test", "Public", "Low Resolution"), landsatId,
    Map("instrument type" -> "satellite", "splines reticulated" -> "0").asJson,
    name, None, mpoly, mpoly, List.empty[String], List.empty[Image.Banded],
    List.empty[Thumbnail.Identified], None,
    SceneFilterFields(cloudCover,
                      Some(Timestamp.from(Instant.parse("2016-09-19T14:41:58.408544Z"))),
                      None,
                      None),
    SceneStatusFields(JobStatus.Processing, JobStatus.Processing, IngestStatus.NotIngested)
  )

}
