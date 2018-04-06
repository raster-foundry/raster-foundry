package com.azavea.rf.datamodel

import com.azavea.rf.datamodel._

import geotrellis.slick.Projected
import geotrellis.vector.{MultiPolygon, Point, Polygon}
import geotrellis.vector.testkit.Rectangle

import io.circe.Json
import io.circe.testing.ArbitraryInstances

import org.scalacheck._
import org.scalacheck.Arbitrary.arbitrary

import java.sql.Timestamp
import java.time.LocalDate
import java.util.UUID

object Generators extends ArbitraryInstances {

  private def nonEmptyStringGen: Gen[String] =
    Gen.nonEmptyListOf[Char](Arbitrary.arbChar.arbitrary).map(_.mkString)

  private def stringWithoutNullBytes: Gen[String] = arbitrary[String] suchThat { ! _.contains('\u0000') }

  private def userRoleGen: Gen[UserRole] = Gen.oneOf(UserRoleRole, Viewer, Admin)

  private def visibilityGen: Gen[Visibility] = Gen.oneOf(
    Visibility.Public, Visibility.Organization, Visibility.Private)

  private def uuidGen: Gen[UUID] = Gen.delay(UUID.randomUUID)

  private def jobStatusGen: Gen[JobStatus] = Gen.oneOf(
    JobStatus.Uploading, JobStatus.Success, JobStatus.Failure, JobStatus.PartialFailure,
    JobStatus.Queued, JobStatus.Processing
  )

  private def ingestStatusGen: Gen[IngestStatus] = Gen.oneOf(
    IngestStatus.NotIngested, IngestStatus.ToBeIngested, IngestStatus.Ingesting,
    IngestStatus.Ingested, IngestStatus.Failed
  )

  private def thumbnailSizeGen: Gen[ThumbnailSize] = Gen.oneOf(
    ThumbnailSize.Small, ThumbnailSize.Large, ThumbnailSize.Square
  )

  private def timestampIn2016Gen: Gen[Timestamp] = for {
    year <- Gen.const(2016)
    month <- Gen.choose(1, 12)
    day <- Gen.choose(1, 28) // for safety
  } yield { Timestamp.valueOf(LocalDate.of(year, month, day).atStartOfDay) }

  private def organizationGen: Gen[Organization] = for {
    name <- arbitrary[String]
  } yield (Organization.Create(name))

  def organizationGen: Gen[Organization] = organizationCreateGen map { _.toOrganization }

  private def userCreateGen: Gen[User.Create] = for {
    id <- arbitrary[String]
    org <- organizationGen
    role <- userRoleGen
  } yield { User.Create(id, org.id, role) }

  private def userGen: Gen[User] = userCreateGen map { _.toUser }

  private def credentialGen: Gen[Credential] = nonEmptyStringGen map { Credential.fromString }

  private def bandIdentifiedGen: Gen[Band.Identified] = for {
    name <- stringWithoutNullBytes
    number <- arbitrary[Int]
    wavelength <- Gen.listOfN(2, arbitrary[Int]) map { _.sorted }
    imageId <- uuidGen
  } yield { Band.Identified(None, imageId, name, number, wavelength) }

  private def bandCreateGen: Gen[Band.Create] = for {
    name <- stringWithoutNullBytes
    number <- arbitrary[Int]
    wavelength <- Gen.listOfN(2, arbitrary[Int]) map { _.sorted }
  } yield { Band.Create(name, number, wavelength) }

  private def bandGen: Gen[Band] = bandIdentifiedGen map { _.toBand }

  private def imageCreateGen: Gen[Image.Create] = for {
    orgId <- uuidGen
    rawDataBytes <- arbitrary[Long]
    visibility <- visibilityGen
    filename <- stringWithoutNullBytes
    sourceUri <- stringWithoutNullBytes
    scene <- uuidGen
    imageMetadata <- arbitrary[Json]
    owner <- arbitrary[Option[String]]
    resolutionMeters <- arbitrary[Float]
    metadataFiles <- Gen.containerOf[List, String](stringWithoutNullBytes)
  } yield (
    Image.Create(
      orgId, rawDataBytes, visibility, filename, sourceUri, scene, imageMetadata,
      owner, resolutionMeters, metadataFiles
    )
  )

  private def imageBandedGen: Gen[Image.Banded] = for {
    orgId <- uuidGen
    rawDataBytes <- arbitrary[Long]
    visibility <- visibilityGen
    filename <- stringWithoutNullBytes
    sourceUri <- stringWithoutNullBytes
    scene <- uuidGen
    imageMetadata <- arbitrary[Json]
    owner <- arbitrary[Option[String]]
    resolutionMeters <- arbitrary[Float]
    metadataFiles <- Gen.containerOf[List, String](stringWithoutNullBytes)
    bands <- Gen.containerOf[Seq, Band.Create](bandCreateGen)
  } yield (
    Image.Banded(
      orgId, rawDataBytes, visibility, filename, sourceUri, owner, scene, imageMetadata,
      resolutionMeters, metadataFiles, bands
    )
  )

  // generate up to a 50km/side polygon with bounds in EPSG:3857 bounds
  private def polygonGen3857: Gen[Polygon] = for {
    width <- Gen.choose(100, 50000)
    height <- Gen.choose(100, 50000)
    centerX <- Gen.choose(-2E7, 2E7)
    centerY <- Gen.choose(-2E7, 2E7)
  } yield {
    Rectangle().withWidth(width).withHeight(height).setCenter(Point(centerX, centerY)).build()
  }

  private def multiPolygonGen3857: Gen[MultiPolygon] = for {
    polygons <- Gen.nonEmptyListOf[Polygon](polygonGen3857)
  } yield (MultiPolygon(polygons))

  def projectedMultiPolygonGen3857: Gen[Projected[MultiPolygon]] =
    multiPolygonGen3857 map { Projected(_, 3857) }

  private def sceneFilterFieldsGen: Gen[SceneFilterFields] = for {
    cloudCover <- Gen.frequency((1, None), (10, arbitrary[Float] map { Some(_) }))
    acquisitionDate <- Gen.frequency((1, None), (10, timestampIn2016Gen map { Some(_) }))
    sunAzimuth <- Gen.frequency((1, None), (10, Gen.choose(0f, 360f) map { Some(_) }))
    sunElevation <- Gen.frequency((1, None), (10, Gen.choose(0f, 90f) map { Some(_) }))
  } yield { SceneFilterFields(cloudCover, acquisitionDate, sunAzimuth, sunElevation) }

  private def sceneStatusFieldsGen: Gen[SceneStatusFields] = for {
    thumbnailStatus <- jobStatusGen
    boundaryStatus <- jobStatusGen
    ingestStatus <- ingestStatusGen
  } yield { SceneStatusFields(thumbnailStatus, boundaryStatus, ingestStatus) }

  private def thumbnailIdentifiedGen: Gen[Thumbnail.Identified] = for {
    id <- uuidGen map { Some(_) }
    organizationId <- uuidGen
    thumbnailSize <- thumbnailSizeGen
    sideLength <- Gen.choose(200, 1000)
    sceneId <- uuidGen
    url <- nonEmptyStringGen
  } yield {
    Thumbnail.Identified(id, organizationId, thumbnailSize, sideLength, sideLength, sceneId, url)
  }

  private def sceneCreateGen: Gen[Scene.Create] = for {
    sceneId <- uuidGen map { Some(_) }
    organizationId <- uuidGen
    ingestSizeBytes <- arbitrary[Int]
    visibility <- visibilityGen
    tags <- Gen.containerOf[List, String](stringWithoutNullBytes)
    datasource <- uuidGen
    sceneMetadata <- arbitrary[Json]
    name <- stringWithoutNullBytes
    owner <- arbitrary[Option[String]]
    tileFootprint <- projectedMultiPolygonGen3857 map { Some(_) }
    dataFootprint <- projectedMultiPolygonGen3857 map { Some(_) }
    metadataFiles <- Gen.containerOf[List, String](stringWithoutNullBytes)
    images <- Gen.containerOf[List, Image.Banded](imageBandedGen)
    thumbnails <- Gen.containerOf[List, Thumbnail.Identified](thumbnailIdentifiedGen)
    ingestLocation <- Gen.oneOf(stringWithoutNullBytes map { Some(_) }, Gen.delay(None))
    filterFields <- sceneFilterFieldsGen
    statusFields <- sceneStatusFieldsGen
  } yield {
    Scene.Create(sceneId, organizationId, ingestSizeBytes, visibility, tags,
                 datasource, sceneMetadata, name, owner, tileFootprint, dataFootprint,
                 metadataFiles, images, thumbnails, ingestLocation, filterFields, statusFields)
  }

  private def imageGen: Gen[Image] = for {
    imCreate <- imageCreateGen
    user <- userGen
  } yield (imCreate.toImage(user))

  object Implicits {
    implicit def arbCredential: Arbitrary[Credential] = Arbitrary { credentialGen }

    implicit def arbOrganization: Arbitrary[Organization] = Arbitrary { organizationGen }

    implicit def arbOrganizationCreate: Arbitrary[Organization.Create] = Arbitrary { organizationCreateGen }

    implicit def arbUserCreate: Arbitrary[User.Create] = Arbitrary { userCreateGen }

    implicit def arbUser: Arbitrary[User] = Arbitrary { userGen }

    implicit def arbBand: Arbitrary[Band] = Arbitrary { bandGen }

    implicit def arbImage: Arbitrary[Image] = Arbitrary { imageGen }

    implicit def arbImageCreate: Arbitrary[Image.Create] = Arbitrary { imageCreateGen }

    implicit def arbImageBanded: Arbitrary[Image.Banded] = Arbitrary { imageBandedGen }
  }
}
