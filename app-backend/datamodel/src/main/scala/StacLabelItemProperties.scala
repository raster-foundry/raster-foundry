package com.rasterfoundry.datamodel

import io.circe.Encoder
import io.circe._
import io.circe.generic.JsonCodec

import java.sql.Timestamp

final case class StacLabelItemProperties(
    property: List[String], // required
    classes: List[StacLabelItemProperties.StacLabelItemClasses], // required
    description: String, // required
    _type: String, // required
    task: Option[List[String]] = None,
    method: Option[List[String]] = None,
    overview: Option[StacLabelItemProperties.StacLabelOverview] = None,
    datetime: Timestamp // required
)

object StacLabelItemProperties {
  implicit val encodeStacLabelItemProperties: Encoder[StacLabelItemProperties] =
    Encoder.forProduct8(
      "label:property",
      "label:classes",
      "label:description",
      "label:type",
      "label:task",
      "label:method",
      "label:overview",
      "datetime"
    )(
      item =>
        (
          item.property,
          item.classes,
          item.description,
          item._type,
          item.task,
          item.method,
          item.overview,
          item.datetime
      )
    )

  @JsonCodec
  final case class StacLabelItemClasses(
      name: String,
      classes: List[String]
  )

  @JsonCodec
  final case class StacLabelOverview(
      propertyKey: String,
      counts: Option[List[Count]] = None,
      statistics: Option[List[Statistics]] = None
  )

  @JsonCodec
  final case class Count(
      className: String,
      count: Int
  )

  @JsonCodec
  final case class Statistics(
      statName: String,
      value: Double
  )
}

final case class StacLabelItemPropertiesThin(
    property: List[String] = List(),
    classes: List[StacLabelItemProperties.StacLabelItemClasses] = List(),
    _type: String = "",
    task: String = ""
)

final case class ProjectStacInfo(
    labelName: Option[String],
    labelGroupName: Option[String]
)
