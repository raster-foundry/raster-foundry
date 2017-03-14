package com.azavea.rf.database.query

import java.util.UUID

import spray.json._
import spray.json.DefaultJsonProtocol._

import com.azavea.rf.datamodel._

//trait QueryParameterJsonFormat extends AdditionalFormats with
//    SerializationUtils {

//  object OrgQueryParamsReader extends JsonReader[OrgQueryParameters] {
//    def read(value: JsValue): OrgQueryParameters = {
//      OrgQueryParameters(
//        jsArrayToList(value.asJsObject.getFields("organizations")(0))
//      )
//    }
//  }

//  object OrgQueryParamsWriter extends JsonWriter[OrgQueryParameters] {
//    def write(params: OrgQueryParameters): JsValue = JsObject(
//      "organizations" -> params.organizations.toJson
//    )
//  }

//  implicit val defaultOrgQueryParamsFormat = jsonFormat(OrgQueryParamsReader, OrgQueryParamsWriter)

//  object UserQueryParamsReader extends JsonReader[UserQueryParameters] {
//    def read(value: JsValue): UserQueryParameters = {
//      val fields = value.asJsObject.getFields("createdBy", "modifiedBy")
//      UserQueryParameters(
//        jsOptionToVal[String](fields(0)),
//        jsOptionToVal[String](fields(1))
//      )
//    }
//  }

//  object UserQueryParamsWriter extends JsonWriter[UserQueryParameters] {
//    def write(params: UserQueryParameters): JsValue = JsObject(
//      "createdBy" -> params.createdBy.toJson,
//      "modifiedBy" -> params.modifiedBy.toJson
//    )
//  }

//  implicit val defaultUserQueryParamsFormat = jsonFormat(UserQueryParamsReader, UserQueryParamsWriter)

//  object TimestampQueryParamsReader extends JsonReader[TimestampQueryParameters] {
//    def read(value: JsValue): TimestampQueryParameters = {
//      val fields = value.asJsObject.getFields(
//        "minCreateDatetime",
//        "maxCreateDatetime",
//        "minModifiedDatetime",
//        "maxModifiedDatetime"
//      )
//      TimestampQueryParameters(
//        formatTs(jsOptionToVal[String](fields(0))),
//        formatTs(jsOptionToVal[String](fields(1))),
//        formatTs(jsOptionToVal[String](fields(2))),
//        formatTs(jsOptionToVal[String](fields(3)))
//      )
//    }
//  }

//  object TimestampQueryParamsWriter extends JsonWriter[TimestampQueryParameters] {
//    def write(params: TimestampQueryParameters): JsValue = JsObject(
//      "minCreateDatetime" -> params.minCreateDatetime.toJson,
//      "maxCreateDatetime" -> params.maxCreateDatetime.toJson,
//      "minModifiedDatetime" -> params.minModifiedDatetime.toJson,
//      "maxModifiedDatetime" -> params.maxModifiedDatetime.toJson
//    )
//  }

//  implicit val defaultTimestampQueryParamsFormat = jsonFormat(TimestampQueryParamsReader, TimestampQueryParamsWriter)

//  object ImageQueryParamsReader extends JsonReader[ImageQueryParameters] {
//    def read(value: JsValue): ImageQueryParameters = {
//      val fields = value.asJsObject.getFields(
//        "minRawDataBytes",
//        "maxRawDataBytes",
//        "minResolution",
//        "maxResolution",
//        "scene"
//      )
//      ImageQueryParameters(
//        jsOptionToVal[Int](fields(0)),
//        jsOptionToVal[Int](fields(1)),
//        jsOptionToVal[Float](fields(2)),
//        jsOptionToVal[Float](fields(3)),
//        jsArrayToList[UUID](fields(4))
//      )
//    }
//  }

//  object ImageQueryParamsWriter extends JsonWriter[ImageQueryParameters] {
//    def write(params: ImageQueryParameters): JsValue = JsObject(
//      "minRawDataBytes" -> params.minRawDataBytes.toJson,
//      "maxRawDataBytes" -> params.maxRawDataBytes.toJson,
//      "minResolution" -> params.minResolution.toJson,
//      "maxResolution" -> params.maxResolution.toJson,
//      "scene" -> params.scene.toJson
//    )
//  }

//  implicit val defaultImageQueryParamsFormat = jsonFormat(ImageQueryParamsReader, ImageQueryParamsWriter)

//  object SceneQueryParamsReader extends JsonReader[SceneQueryParameters] {
//    def read(value: JsValue): SceneQueryParameters = {
//      val fields = value.asJsObject.getFields(
//        "maxCloudCover", // 0
//        "minCloudCover",
//        "minAcquisitionDatetime",
//        "maxAcquisitionDatetime",
//        "datasource",
//        "month", // 5
//        "minDayOfMonth",
//        "maxDayOfMonth",
//        "maxSunAzimuth",
//        "minSunAzimuth",
//        "maxSunElevation", // 10
//        "minSunElevation",
//        "bbox",
//        "point",
//        "project",
//        "ingested", // 15
//        "ingestStatus"
//      )

//      SceneQueryParameters(
//        jsOptionToVal[Float](fields(0)),
//        jsOptionToVal[Float](fields(1)),
//        formatTs(jsOptionToVal[String](fields(2))),
//        formatTs(jsOptionToVal[String](fields(3))),
//        jsArrayToList[UUID](fields(4)),
//        jsArrayToList[Int](fields(5)),
//        jsOptionToVal[Int](fields(6)),
//        jsOptionToVal[Int](fields(7)),
//        jsOptionToVal[Float](fields(8)),
//        jsOptionToVal[Float](fields(9)),
//        jsOptionToVal[Float](fields(10)),
//        jsOptionToVal[Float](fields(11)),
//        jsOptionToVal[String](fields(12)),
//        jsOptionToVal[String](fields(13)),
//        jsOptionToVal[UUID](fields(14)),
//        jsOptionToVal[Boolean](fields(15)),
//        jsArrayToList[String](fields(16))
//      )
//    }
//  }

//  object SceneQueryParamsWriter extends JsonWriter[SceneQueryParameters] {
//    def write(params: SceneQueryParameters): JsValue = JsObject(
//      "maxCloudCover" -> params.maxCloudCover.toJson,
//      "minCloudCover" -> params.minCloudCover.toJson,
//      "minAcquisitionDatetime" -> params.minAcquisitionDatetime.toJson,
//      "maxAcquisitionDatetime" -> params.maxAcquisitionDatetime.toJson,
//      "datasource" -> params.datasource.toJson,
//      "month" -> params.month.toJson,
//      "minDayOfMonth" -> params.minDayOfMonth.toJson,
//      "maxDayOfMonth" -> params.maxDayOfMonth.toJson,
//      "maxSunAzimuth" -> params.maxSunAzimuth.toJson,
//      "minSunAzimuth" -> params.minSunAzimuth.toJson,
//      "maxSunElevation" -> params.maxSunElevation.toJson,
//      "minSunElevation" -> params.minSunElevation.toJson,
//      "bbox" -> params.bbox.toJson,
//      "point" -> params.point.toJson,
//      "project" -> params.project.toJson,
//      "ingested" -> params.ingested.toJson,
//      "ingestStatus" -> params.ingestStatus.toJson
//    )
//  }

//  implicit val defaultSceneQueryParamsFormat = jsonFormat(SceneQueryParamsReader, SceneQueryParamsWriter)

//  object CombinedSceneQueryParamsReader extends JsonReader[CombinedSceneQueryParams] {
//    def read(value: JsValue): CombinedSceneQueryParams = {
//      val fields = value.asJsObject.getFields(
//        "orgParams",
//        "userParams",
//        "timestampParams",
//        "sceneParams",
//        "imageParams"
//      )

//      CombinedSceneQueryParams(
//        OrgQueryParamsReader.read(fields(0)),
//        UserQueryParamsReader.read(fields(1)),
//        TimestampQueryParamsReader.read(fields(2)),
//        SceneQueryParamsReader.read(fields(3)),
//        ImageQueryParamsReader.read(fields(4))
//      )
//    }
//  }

//  object CombinedSceneQueryParamsWriter extends JsonWriter[CombinedSceneQueryParams] {
//    def write(params: CombinedSceneQueryParams): JsValue = JsObject(
//      "orgParams" -> params.orgParams.toJson,
//      "userParams" -> params.userParams.toJson,
//      "timestampParams" -> params.timestampParams.toJson,
//      "sceneParams" -> params.sceneParams.toJson,
//      "imageParams" -> params.imageParams.toJson
//    )
//  }

//  implicit val defaultCombinedSceneQueryParamsFormat = jsonFormat(
//    CombinedSceneQueryParamsReader, CombinedSceneQueryParamsWriter
//  )
//}
