package com.azavea.rf.datamodel

import java.sql.Timestamp
import java.util.UUID
import spray.json._

import geotrellis.vector.Geometry
import geotrellis.slick.Projected

object ScenesJsonProtocol extends DefaultJsonProtocol {

  def jsArrayToList[T](jsArr: JsValue): List[T] = {
    jsArr match {
      case arr: JsArray => arr.elements.map(_.asInstanceOf[T]).to[List]
      case _ => List.empty[T]
    }
  }

  // Reimplements OptionFormat.read because OptionFormat is mysteriously not in scope
  def jsOptionToVal[T](jsOpt: JsValue): Option[T] = {
    jsOpt match {
      case JsNull => None
      case v: Any => Some(v.asInstanceOf[T])
    }
  }

  def formatTs(ts: String): Timestamp = {
    Timestamp.valueOf(
      ts.replace("Z", "").replace("T", " ")
    )
  }

  implicit object SceneWithRelatedFormat extends RootJsonFormat[Scene.WithRelated] {
    def write(scene: Scene.WithRelated): JsObject = JsObject(
      "id" -> scene.id.toJson,
      "createdAt" -> scene.createdAt.toJson,
      "createdBy" -> scene.createdBy.toJson,
      "modifiedAt" -> scene.modifiedAt.toJson,
      "modifiedBy" -> scene.modifiedBy.toJson,
      "organizationId" -> scene.organizationId.toJson,
      "ingestSizeBytes" -> scene.ingestSizeBytes.toJson,
      "visibility" -> scene.visibility.toJson,
      "tags" -> scene.tags.toJson,
      "datasource" -> scene.datasource.toJson,
      "sceneMetadata" -> scene.sceneMetadata.toJson,
      "name" -> scene.name.toJson,
      "tileFootprint" -> scene.tileFootprint.toJson,
      "dataFootprint" -> scene.dataFootprint.toJson,
      "metadataFiles" -> scene.metadataFiles.toJson,
      "images" -> scene.images.toJson,
      "thumbnails" -> scene.thumbnails.toJson,
      "ingestLocation" -> scene.ingestLocation.toJson,
      "filterFields" -> scene.filterFields.toJson,
      "statusFields" -> scene.statusFields.toJson
    )

    def read(value: JsValue): Scene.WithRelated = {
      val jsObject = value.asJsObject
      val fields = jsObject.getFields(
        "id", // 0
        "createdAt", // 1
        "createdBy", // 2
        "modifiedAt", // 3
        "modifiedBy", // 4
        "organizationId", // 5
        "ingestSizeBytes", // 6
        "visibility", // 7
        "tags", // 8
        "datasource", // 9
        "sceneMetadata", // 10
        "name", // 11
        "tileFootprint", // 12
        "dataFootprint", // 13
        "metadataFiles", // 14
        "images", // 15
        "thumbnails", // 16
        "ingestLocation", // 17
        "ingestStatus", // 18
        "filterFields", // 19
        "statusFields" // 20
      )

      Scene.WithRelated(
        UUID.fromString(StringJsonFormat.read(fields(0))), // id
        formatTs(StringJsonFormat.read(fields(1))), // createdAt
        StringJsonFormat.read(fields(2)), // createdBy
        formatTs(StringJsonFormat.read(fields(3))), // modifiedAt
        StringJsonFormat.read(fields(4)), // modifiedBy
        UUID.fromString(StringJsonFormat.read(fields(5))), // organizationId
        IntJsonFormat.read(fields(6)), // ingestSizeBytes
        Visibility.fromString(StringJsonFormat.read(fields(7))), // visibility
        jsArrayToList[String](fields(8)), // tags
        UUID.fromString(StringJsonFormat.read(fields(9))), // datasource
        fields(10).convertTo[Map[String, Any]], // sceneMetadata
        StringJsonFormat.read(fields(11)), // name
        jsOptionToVal[Projected[Geometry]](fields(12)), // tileFootprint
        jsOptionToVal[Projected[Geometry]](fields(13)), // dataFootprint
        jsArrayToList[String](fields(14)), // metadataFiles
        jsArrayToList[Image.WithRelated](fields(15)), // images
        jsArrayToList[Thumbnail](fields(16)), // thumbnails
        jsOptionToVal[String](fields(17)), // ingestLocation
        fields(18).convertTo[SceneFilterFields], // filterFields
        fields(19).convertTo[SceneStatusFields] // statusFields
      )
    }
  }
}
