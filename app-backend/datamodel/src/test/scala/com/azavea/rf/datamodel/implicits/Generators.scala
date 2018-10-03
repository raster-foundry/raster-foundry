package com.azavea.rf.datamodel

import java.sql.Timestamp
import java.time.LocalDate
import java.util.UUID

import com.lonelyplanet.akka.http.extensions.{Order, PageRequest}
import geotrellis.vector.testkit.Rectangle
import geotrellis.vector.{MultiPolygon, Point, Polygon, Projected}
import io.circe.syntax._
import io.circe.testing.ArbitraryInstances
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck._

object Generators extends ArbitraryInstances {

  // This is only necessary until a Platform generator is supported
  val defaultPlatformId: UUID =
    UUID.fromString("31277626-968b-4e40-840b-559d9c67863c")

  private def stringListGen: Gen[List[String]] =
    Gen.oneOf(0, 15) flatMap { Gen.listOfN(_, nonEmptyStringGen) }

  private def nonEmptyStringGen: Gen[String] =
    Gen.nonEmptyListOf[Char](Gen.alphaChar) map { _.mkString }

  private def possiblyEmptyStringGen: Gen[String] =
    Gen.containerOf[List, Char](Gen.alphaChar) map { _.mkString }

  private def pageRequestGen: Gen[PageRequest] =
    Gen.const(PageRequest(0, 20, Map("created_at" -> Order.Desc)))

  private def userRoleGen: Gen[UserRole] =
    Gen.oneOf(UserRoleRole, Viewer, Admin)

  private def groupTypeGen: Gen[GroupType] =
    Gen.oneOf(GroupType.Platform, GroupType.Organization, GroupType.Team)

  private def groupRoleGen: Gen[GroupRole] =
    Gen.oneOf(GroupRole.Admin, GroupRole.Member)

  private def subjectTypeGen: Gen[SubjectType] = Gen.oneOf(
    SubjectType.All,
    SubjectType.Platform,
    SubjectType.Organization,
    SubjectType.Team,
    SubjectType.User
  )

  private def actionTypeGen: Gen[ActionType] = Gen.oneOf(
    ActionType.View,
    ActionType.Edit,
    ActionType.Deactivate,
    ActionType.Delete,
    ActionType.Annotate,
    ActionType.Export,
    ActionType.Download
  )

  private def annotationQualityGen: Gen[AnnotationQuality] = Gen.oneOf(
    AnnotationQuality.Yes,
    AnnotationQuality.No,
    AnnotationQuality.Miss,
    AnnotationQuality.Unsure
  )

  private def visibilityGen: Gen[Visibility] =
    Gen.oneOf(Visibility.Public, Visibility.Organization, Visibility.Private)

  private def userVisibilityGen: Gen[UserVisibility] =
    Gen.oneOf(UserVisibility.Public, UserVisibility.Private)

  private def orgStatusGen: Gen[OrgStatus] =
    Gen.oneOf(OrgStatus.Requested, OrgStatus.Active, OrgStatus.Inactive)

  private def sceneTypeGen: Gen[SceneType] =
    Gen.oneOf(SceneType.Avro, SceneType.COG)

  private def credentialGen: Gen[Credential] = possiblyEmptyStringGen flatMap {
    Credential.fromString
  }

  // This is fine not to test the max value --
  private def rawDataBytesGen: Gen[Long] = Gen.choose(0L, 100000L)

  private def bandDataTypeGen: Gen[BandDataType] = Gen.oneOf(
    BandDataType.Diverging,
    BandDataType.Sequential,
    BandDataType.Categorical
  )

  private def uuidGen: Gen[UUID] = Gen.delay(UUID.randomUUID)

  private def jobStatusGen: Gen[JobStatus] = Gen.oneOf(
    JobStatus.Uploading,
    JobStatus.Success,
    JobStatus.Failure,
    JobStatus.PartialFailure,
    JobStatus.Queued,
    JobStatus.Processing
  )

  private def ingestStatusGen: Gen[IngestStatus] = Gen.oneOf(
    IngestStatus.NotIngested,
    IngestStatus.ToBeIngested,
    IngestStatus.Ingesting,
    IngestStatus.Ingested,
    IngestStatus.Failed
  )

  private def shapePropertiesGen: Gen[ShapeProperties] =
    for {
      timeField <- timestampIn2016Gen
      userField <- nonEmptyStringGen
      name <- nonEmptyStringGen
      description <- Gen.oneOf(Gen.const(None), nonEmptyStringGen map {
        Some(_)
      })
    } yield {
      ShapeProperties(
        timeField,
        userField,
        timeField,
        userField,
        userField,
        name,
        description
      )
    }

  private def thumbnailSizeGen: Gen[ThumbnailSize] =
    Gen.oneOf(ThumbnailSize.Small, ThumbnailSize.Large, ThumbnailSize.Square)

  private def uploadStatusGen: Gen[UploadStatus] = Gen.oneOf(
    UploadStatus.Created,
    UploadStatus.Uploading,
    UploadStatus.Uploaded,
    UploadStatus.Queued,
    UploadStatus.Processing,
    UploadStatus.Complete,
    UploadStatus.Failed,
    UploadStatus.Aborted
  )

  private def uploadTypeGen: Gen[UploadType] = Gen.oneOf(
    UploadType.Dropbox,
    UploadType.S3,
    UploadType.Local,
    UploadType.Planet
  )

  private def fileTypeGen: Gen[FileType] =
    Gen.oneOf(FileType.Geotiff, FileType.GeotiffWithMetadata)

  private def userVisibility: Gen[UserVisibility] =
    Gen.oneOf(UserVisibility.Public, UserVisibility.Private)

  private def timestampIn2016Gen: Gen[Timestamp] =
    for {
      year <- Gen.const(2016)
      month <- Gen.choose(1, 12)
      day <- Gen.choose(1, 28) // for safety
    } yield { Timestamp.valueOf(LocalDate.of(year, month, day).atStartOfDay) }

  // generate up to a 50km/side polygon with bounds in EPSG:3857 bounds
  private def polygonGen3857: Gen[Polygon] =
    for {
      width <- Gen.choose(100, 50000)
      height <- Gen.choose(100, 50000)
      centerX <- Gen.choose(-2E7, 2E7)
      centerY <- Gen.choose(-2E7, 2E7)
    } yield {
      Rectangle()
        .withWidth(width)
        .withHeight(height)
        .setCenter(Point(centerX, centerY))
        .build()
    }

  private def multiPolygonGen3857: Gen[MultiPolygon] =
    for {
      polygons <- Gen.oneOf(1, 2) flatMap {
        Gen.listOfN[Polygon](_, polygonGen3857)
      }
    } yield MultiPolygon(polygons)

  private def projectedMultiPolygonGen3857: Gen[Projected[MultiPolygon]] =
    multiPolygonGen3857 map { Projected(_, 3857) }

  private def annotationGroupCreateGen: Gen[AnnotationGroup.Create] =
    for {
      name <- nonEmptyStringGen
      defaultStyle <- Gen.const(Some(().asJson))
    } yield { AnnotationGroup.Create(name, defaultStyle) }

  private def annotationCreateGen: Gen[Annotation.Create] =
    for {
      owner <- Gen.const(None)
      label <- nonEmptyStringGen
      description <- nonEmptyStringGen map { Some(_) }
      machineGenerated <- arbitrary[Option[Boolean]]
      confidence <- Gen.choose(0.0f, 1.0f) map { Some(_) }
      quality <- annotationQualityGen map { Some(_) }
      geometry <- projectedMultiPolygonGen3857 map { Some(_) }
    } yield {
      Annotation.Create(
        owner,
        label,
        description,
        machineGenerated,
        confidence,
        quality,
        geometry,
        None
      )
    }

  private def organizationCreateGen: Gen[Organization.Create] =
    for {
      name <- nonEmptyStringGen
      visibility <- visibilityGen
      orgStatus <- orgStatusGen
    } yield
      Organization
        .Create(name, defaultPlatformId, Some(visibility), orgStatus)

  private def organizationGen: Gen[Organization] = organizationCreateGen map {
    _.toOrganization(true)
  }

  private def shapeCreateGen: Gen[Shape.Create] =
    for {
      owner <- Gen.const(None)
      name <- nonEmptyStringGen
      description <- nonEmptyStringGen map { Some(_) }
      geometry <- projectedMultiPolygonGen3857
    } yield {
      Shape.Create(owner, name, description, geometry)
    }

  private def shapeGeoJSONGen: Gen[Shape.GeoJSON] =
    for {
      id <- uuidGen
      geometry <- projectedMultiPolygonGen3857
      properties <- shapePropertiesGen
    } yield {
      Shape.GeoJSON(id, Some(geometry), properties)
    }

  private def userCreateGen: Gen[User.Create] =
    for {
      id <- uuidGen map { _.toString }
      role <- userRoleGen
      email <- nonEmptyStringGen
      name <- nonEmptyStringGen
      profileImageUri <- nonEmptyStringGen
    } yield { User.Create(id, role, email, name, profileImageUri) }

  private def userJwtFieldsGen: Gen[User.JwtFields] =
    for {
      userCreate <- userCreateGen
      placeholderUUID <- uuidGen
    } yield {
      User.JwtFields(
        userCreate.id,
        userCreate.email,
        userCreate.name,
        userCreate.profileImageUri,
        placeholderUUID,
        placeholderUUID
      )
    }

  private def userGen: Gen[User] = userCreateGen map { _.toUser }

  private def bandIdentifiedGen: Gen[Band.Identified] =
    for {
      name <- nonEmptyStringGen
      number <- Gen.choose(1, 15)
      wavelength <- Gen.listOfN(2, Gen.choose(1, 50000)) map { _.sorted }
      imageId <- uuidGen
    } yield { Band.Identified(None, imageId, name, number, wavelength) }

  private def bandCreateGen: Gen[Band.Create] =
    for {
      name <- nonEmptyStringGen
      number <- Gen.choose(1, 15)
      wavelength <- Gen.listOfN(2, Gen.choose(1, 50000)) map { _.sorted }
    } yield { Band.Create(name, number, wavelength) }

  private def bandGen: Gen[Band] = bandIdentifiedGen map { _.toBand }

  private def singleBandOptionsParamsGen: Gen[SingleBandOptions.Params] =
    for {
      band <- Gen.choose(1, 15)
      datatype <- bandDataTypeGen
      colorBins <- Gen.choose(3, 17)
      colorScheme <- Gen.const(().asJson)
      legendOrientation <- nonEmptyStringGen
    } yield {
      SingleBandOptions.Params(
        band,
        datatype,
        colorBins,
        colorScheme,
        legendOrientation
      )
    }

  private def imageCreateGen: Gen[Image.Create] =
    for {
      rawDataBytes <- rawDataBytesGen
      visibility <- visibilityGen
      filename <- nonEmptyStringGen
      sourceUri <- nonEmptyStringGen
      scene <- uuidGen
      imageMetadata <- Gen.const(().asJson)
      owner <- arbitrary[Option[String]]
      resolutionMeters <- Gen.choose(0.25f, 1000f)
      metadataFiles <- stringListGen
    } yield
      Image.Create(
        rawDataBytes,
        visibility,
        filename,
        sourceUri,
        scene,
        imageMetadata,
        owner,
        resolutionMeters,
        metadataFiles
      )

  private def imageBandedGen: Gen[Image.Banded] =
    for {
      rawDataBytes <- rawDataBytesGen
      visibility <- visibilityGen
      filename <- nonEmptyStringGen
      sourceUri <- nonEmptyStringGen
      scene <- uuidGen
      imageMetadata <- Gen.const(().asJson)
      owner <- arbitrary[Option[String]]
      resolutionMeters <- Gen.choose(0.25f, 1000f)
      metadataFiles <- stringListGen
      bands <- Gen.listOfN(3, bandCreateGen)
    } yield
      Image.Banded(
        rawDataBytes,
        visibility,
        filename,
        sourceUri,
        owner,
        scene,
        imageMetadata,
        resolutionMeters,
        metadataFiles,
        bands
      )
  private def imageGen: Gen[Image] =
    for {
      imCreate <- imageCreateGen
      user <- userGen
    } yield imCreate.copy(owner = Some(user.id)).toImage(user)

  private def projectCreateGen: Gen[Project.Create] =
    for {
      name <- nonEmptyStringGen
      description <- nonEmptyStringGen
      visibility <- visibilityGen
      tileVisibility <- visibilityGen
      isAOIProject <- arbitrary[Boolean]
      aoiCadenceMillis <- Gen.choose(0L, 604800000L)
      owner <- Gen.const(None)
      tags <- stringListGen
      isSingleBand <- arbitrary[Boolean]
      singleBandOptions <- singleBandOptionsParamsGen map { Some(_) }
      extras <- Gen.const(().asJson)
    } yield {
      Project.Create(
        name,
        description,
        visibility,
        tileVisibility,
        isAOIProject,
        aoiCadenceMillis,
        owner,
        tags,
        isSingleBand,
        singleBandOptions,
        Some(extras)
      )
    }

  private def projectGen: Gen[Project] =
    for {
      projCreate <- projectCreateGen
      user <- userGen
    } yield { projCreate.copy(owner = Some(user.id)).toProject(user) }

  private def sceneFilterFieldsGen: Gen[SceneFilterFields] =
    for {
      cloudCover <- Gen.frequency((1, None), (10, Gen.choose(0.0f, 1.0f) map {
        Some(_)
      }))
      acquisitionDate <- Gen.frequency((1, None), (10, timestampIn2016Gen map {
        Some(_)
      }))
      sunAzimuth <- Gen.frequency((1, None), (10, Gen.choose(0f, 360f) map {
        Some(_)
      }))
      sunElevation <- Gen.frequency((1, None), (10, Gen.choose(0f, 90f) map {
        Some(_)
      }))
    } yield {
      SceneFilterFields(cloudCover, acquisitionDate, sunAzimuth, sunElevation)
    }

  private def sceneStatusFieldsGen: Gen[SceneStatusFields] =
    for {
      thumbnailStatus <- jobStatusGen
      boundaryStatus <- jobStatusGen
      ingestStatus <- ingestStatusGen
    } yield { SceneStatusFields(thumbnailStatus, boundaryStatus, ingestStatus) }

  private def thumbnailIdentifiedGen: Gen[Thumbnail.Identified] =
    for {
      id <- uuidGen map { Some(_) }
      thumbnailSize <- thumbnailSizeGen
      sideLength <- Gen.choose(200, 1000)
      sceneId <- uuidGen
      url <- nonEmptyStringGen
    } yield {
      Thumbnail.Identified(
        id,
        thumbnailSize,
        sideLength,
        sideLength,
        sceneId,
        url
      )
    }

  private def thumbnailGen: Gen[Thumbnail] =
    for {
      thumbnailIdentified <- thumbnailIdentifiedGen
      userId <- nonEmptyStringGen
    } yield { thumbnailIdentified.toThumbnail }

  private def sceneCreateGen: Gen[Scene.Create] =
    for {
      sceneId <- uuidGen map { Some(_) }
      visibility <- Gen.const(Visibility.Private)
      tags <- stringListGen
      datasource <- uuidGen
      sceneMetadata <- Gen.const(().asJson)
      name <- nonEmptyStringGen
      owner <- arbitrary[Option[String]]
      tileFootprint <- projectedMultiPolygonGen3857 map { Some(_) }
      dataFootprint <- projectedMultiPolygonGen3857 map { Some(_) }
      metadataFiles <- stringListGen
      images <- Gen.oneOf(1, 10) flatMap { Gen.listOfN(_, imageBandedGen) }
      thumbnails <- Gen.oneOf(1, 2) flatMap {
        Gen.listOfN(_, thumbnailIdentifiedGen)
      }
      ingestLocation <- Gen.oneOf(
        nonEmptyStringGen map { Some(_) },
        Gen.const(None)
      )
      filterFields <- sceneFilterFieldsGen
      statusFields <- sceneStatusFieldsGen
      sceneType <- Gen.option(sceneTypeGen)
    } yield {
      Scene.Create(
        sceneId,
        visibility,
        tags,
        datasource,
        sceneMetadata,
        name,
        owner,
        tileFootprint,
        dataFootprint,
        metadataFiles,
        images,
        thumbnails,
        ingestLocation,
        filterFields,
        statusFields,
        sceneType
      )
    }

  private def aoiCreateGen: Gen[AOI.Create] =
    for {
      shape <- uuidGen
      filters <- Gen.const(().asJson) // maybe this should be CombinedSceneQueryParams as json
      owner <- Gen.const(None)
      isActive <- arbitrary[Boolean]
      startTime <- timestampIn2016Gen
      approvalRequired <- arbitrary[Boolean]
    } yield {
      AOI.Create(shape, filters, owner, isActive, startTime, approvalRequired)
    }

  private def aoiGen: Gen[AOI] =
    for {
      id <- uuidGen
      timeField <- timestampIn2016Gen
      userField <- nonEmptyStringGen
      shape <- uuidGen
      filters <- Gen.const(().asJson) // maybe this should be CombinedSceneQueryParams as json
      isActive <- arbitrary[Boolean]
      startTime <- timestampIn2016Gen
      approvalRequired <- arbitrary[Boolean]
      projectId <- uuidGen
    } yield {
      AOI(
        id,
        timeField,
        timeField,
        userField,
        userField,
        userField,
        shape,
        filters,
        isActive,
        startTime,
        approvalRequired,
        projectId
      )
    }

  private def datasourceCreateGen: Gen[Datasource.Create] =
    for {
      name <- nonEmptyStringGen
      visibility <- visibilityGen
      owner <- Gen.const(None)
      composites <- Gen.delay(().asJson)
      extras <- Gen.delay(().asJson)
      bands <- Gen.delay(().asJson)
      licenseName <- Gen.oneOf(None, Some("GPL-3.0"))
    } yield {
      Datasource.Create(
        name,
        visibility,
        owner,
        composites,
        extras,
        bands,
        licenseName
      )
    }

  private def uploadCreateGen: Gen[Upload.Create] =
    for {
      uploadStatus <- uploadStatusGen
      fileType <- fileTypeGen
      uploadType <- uploadTypeGen
      files <- stringListGen
      datasource <- uuidGen
      metadata <- Gen.const(().asJson)
      owner <- Gen.const(None)
      visibility <- visibilityGen
      projectId <- Gen.const(None)
      source <- Gen.oneOf(nonEmptyStringGen map { Some(_) }, Gen.const(None))
    } yield {
      Upload.Create(
        uploadStatus,
        fileType,
        uploadType,
        files,
        datasource,
        metadata,
        owner,
        visibility,
        projectId,
        source
      )
    }

  private def layerAttributeGen: Gen[LayerAttribute] =
    for {
      layerName <- nonEmptyStringGen
      zoom <- Gen.choose(0, 30)
      name <- nonEmptyStringGen
      value <- Gen.const(().asJson)
    } yield {
      LayerAttribute(layerName, zoom, name, value)
    }

  private def layerAttributesWithSameLayerNameGen: Gen[List[LayerAttribute]] =
    for {
      layerName <- nonEmptyStringGen
      layerAttributes <- Gen.listOfN(10, layerAttributeGen)
    } yield layerAttributes map { _.copy(layerName = layerName) }

  private def combinedSceneQueryParamsGen: Gen[CombinedSceneQueryParams] =
    Gen.const(CombinedSceneQueryParams())

  private def teamCreateGen: Gen[Team.Create] =
    for {
      orgId <- uuidGen
      name <- nonEmptyStringGen
      settings <- Gen.const(().asJson)
    } yield Team.Create(orgId, name, settings)

  private def teamGen: Gen[Team] =
    for {
      user <- userGen
      teamCreate <- teamCreateGen
    } yield {
      teamCreate.toTeam(user)
    }

  private def userGroupRoleCreateGen: Gen[UserGroupRole.Create] =
    for {
      user <- userGen
      groupType <- groupTypeGen
      groupId <- uuidGen
      groupRole <- groupRoleGen
    } yield { UserGroupRole.Create(user.id, groupType, groupId, groupRole) }

  private def platformPublicSettingsGen: Gen[Platform.PublicSettings] =
    for {
      emailUser <- nonEmptyStringGen
      emailSmtpHost <- nonEmptyStringGen
      emailSmtpPort <- Gen.oneOf(25, 2525, 465, 587)
      emailSmtpEncryption <- Gen.oneOf("ssl", "tls", "starttls")
      emailIngestNotification <- arbitrary[Boolean]
      emailAoiNotification <- arbitrary[Boolean]
      emailExportNotification <- arbitrary[Boolean]
      platformHost <- Gen.const(None)
    } yield {
      Platform.PublicSettings(
        emailUser,
        emailSmtpHost,
        emailSmtpPort,
        emailSmtpEncryption,
        emailIngestNotification,
        emailAoiNotification,
        emailExportNotification,
        platformHost
      )
    }

  private def platformPrivateSettingsGen: Gen[Platform.PrivateSettings] =
    for {
      emailPassword <- nonEmptyStringGen
    } yield { Platform.PrivateSettings(emailPassword) }

  private def platformGen: Gen[Platform] =
    for {
      platformId <- uuidGen
      platformName <- uuidGen map { _.toString }
      publicSettings <- platformPublicSettingsGen
      isActive <- arbitrary[Boolean]
      defaultOrganizationId <- Gen.const(None)
      privateSettings <- platformPrivateSettingsGen
    } yield {
      Platform(
        platformId,
        platformName,
        publicSettings,
        isActive,
        defaultOrganizationId,
        privateSettings
      )
    }

  private def userOrgPlatformGen
    : Gen[(User.Create, Organization.Create, Platform)] =
    for {
      platform <- platformGen
      orgCreate <- organizationCreateGen map {
        _.copy(platformId = platform.id)
      }
      userCreate <- userCreateGen
    } yield { (userCreate, orgCreate, platform) }

  private def searchQueryParametersGen: Gen[SearchQueryParameters] =
    for {
      searchName <- possiblyEmptyStringGen
    } yield { SearchQueryParameters(Some(searchName)) }

  private def objectAccessControlRuleGen: Gen[ObjectAccessControlRule] = for {
    subjectType <- subjectTypeGen
    subjectId <- uuidGen
    actionType <- actionTypeGen
  } yield { ObjectAccessControlRule(
    subjectType,
    subjectType match {
      case SubjectType.All => None
      case _ => Some(subjectId.toString)
    },
    actionType) }

  object Implicits {
    implicit def arbCredential: Arbitrary[Credential] = Arbitrary {
      credentialGen
    }

    implicit def arbPageRequest: Arbitrary[PageRequest] = Arbitrary {
      pageRequestGen
    }

    implicit def arbCombinedSceneQueryParams
      : Arbitrary[CombinedSceneQueryParams] = Arbitrary {
      combinedSceneQueryParamsGen
    }

    implicit def arbAnnotationCreate: Arbitrary[Annotation.Create] = Arbitrary {
      annotationCreateGen
    }

    implicit def arbListAnnotationCreate: Arbitrary[List[Annotation.Create]] =
      Arbitrary {
        Gen.listOfN(10, arbitrary[Annotation.Create])
      }

    implicit def arbAnnotationGroupCreate: Arbitrary[AnnotationGroup.Create] =
      Arbitrary { annotationGroupCreateGen }

    implicit def arbOrganization: Arbitrary[Organization] = Arbitrary {
      organizationGen
    }

    implicit def arbOrganizationCreate: Arbitrary[Organization.Create] =
      Arbitrary { organizationCreateGen }

    implicit def arbUserCreate: Arbitrary[User.Create] = Arbitrary {
      userCreateGen
    }

    implicit def arbUser: Arbitrary[User] = Arbitrary { userGen }

    implicit def arbBand: Arbitrary[Band] = Arbitrary { bandGen }

    implicit def arbImage: Arbitrary[Image] = Arbitrary { imageGen }

    implicit def arbImageCreate: Arbitrary[Image.Create] = Arbitrary {
      imageCreateGen
    }

    implicit def arbImageBanded: Arbitrary[Image.Banded] = Arbitrary {
      imageBandedGen
    }

    implicit def arbProjectCreate: Arbitrary[Project.Create] = Arbitrary {
      projectCreateGen
    }

    implicit def arbProject: Arbitrary[Project] = Arbitrary { projectGen }

    implicit def arbSceneCreate: Arbitrary[Scene.Create] = Arbitrary {
      sceneCreateGen
    }

    implicit def arbShapeCreate: Arbitrary[Shape.Create] = Arbitrary {
      shapeCreateGen
    }

    implicit def arbShapeGeoJSON: Arbitrary[Shape.GeoJSON] = Arbitrary {
      shapeGeoJSONGen
    }

    implicit def arbListSceneCreate: Arbitrary[List[Scene.Create]] = Arbitrary {
      Gen.listOfN(3, sceneCreateGen)
    }

    implicit def arbThumbnail: Arbitrary[Thumbnail] = Arbitrary { thumbnailGen }

    implicit def arbDatasourceCreate: Arbitrary[Datasource.Create] = Arbitrary {
      datasourceCreateGen
    }

    implicit def arbUploadCreate: Arbitrary[Upload.Create] = Arbitrary {
      uploadCreateGen
    }

    implicit def arbAOICreate: Arbitrary[AOI.Create] = Arbitrary {
      aoiCreateGen
    }

    implicit def arbAOI: Arbitrary[AOI] = Arbitrary { aoiGen }

    implicit def arbLayerAttribute: Arbitrary[LayerAttribute] = Arbitrary {
      layerAttributeGen
    }

    implicit def arbListLayerAttribute: Arbitrary[List[LayerAttribute]] =
      Arbitrary {
        layerAttributesWithSameLayerNameGen
      }

    implicit def arbTeamCreate: Arbitrary[Team.Create] = Arbitrary {
      teamCreateGen
    }

    implicit def arbTeam: Arbitrary[Team] = Arbitrary { teamGen }

    implicit def arbUserGroupRoleCreate: Arbitrary[UserGroupRole.Create] =
      Arbitrary { userGroupRoleCreateGen }

    implicit def arbGroupRoleCreate: Arbitrary[GroupRole] = Arbitrary {
      groupRoleGen
    }

    implicit def arbPlatform: Arbitrary[Platform] = Arbitrary { platformGen }

    implicit def arbUserOrgPlatform
      : Arbitrary[(User.Create, Organization.Create, Platform)] = Arbitrary {
      userOrgPlatformGen
    }

    implicit def arbUserJwtFields: Arbitrary[User.JwtFields] = Arbitrary {
      userJwtFieldsGen
    }

    implicit def arbUserVisibility: Arbitrary[UserVisibility] = Arbitrary {
      userVisibilityGen
    }

    implicit def arbSearchQueryParameters: Arbitrary[SearchQueryParameters] =
      Arbitrary { searchQueryParametersGen }


    implicit def arbObjectAccessControlRule : Arbitrary[ObjectAccessControlRule] =
      Arbitrary { objectAccessControlRuleGen }

    implicit def arbListObjectAccessControlRule: Arbitrary[List[ObjectAccessControlRule]] =
      Arbitrary {
        Gen.nonEmptyListOf[ObjectAccessControlRule](arbitrary[ObjectAccessControlRule])
      }
  }
}
