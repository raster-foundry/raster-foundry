package com.azavea.rf.datamodel

import io.circe.generic.JsonCodec
import io.circe.generic.extras._

/**
 * Case class for paginated results
 *
 * @param count number of total results available
 * @param hasPrevious whether or not previous results are available
 * @param hasNext whether or not additional results are available
 * @param page current page of results
 * @param pageSize number of results per page
 * @param results sequence of results for a page
 */

object GeoJsonCodec {

  implicit val config: Configuration = Configuration.default.copy(
    transformMemberNames = {
      case "_type" => "type"
      case other => other
    }
  )

  @ConfiguredJsonCodec
  case class GeoJsonFeatureCollection[T <: GeoJSONFeature](
    features: Seq[T],
    _type: String = "FeatureCollection"
  )

  @ConfiguredJsonCodec
  case class PaginatedGeoJsonResponse[T <: GeoJSONFeature](
    count: Int,
    hasPrevious: Boolean,
    hasNext: Boolean,
    page: Int,
    pageSize: Int,
    features: Seq[T],
    _type: String = "FeatureCollection"
  )

  def fromSeqToFeatureCollection[T1 <: GeoJSONSerializable[T2], T2 <: GeoJSONFeature](features: Seq[T1]): GeoJsonFeatureCollection[T2] = {
    GeoJsonFeatureCollection[T2](
      features map { _.toGeoJSONFeature },
      "FeatureCollection"
    )
  }

  def fromPaginatedResponseToGeoJson[T1 <: GeoJSONSerializable[T2], T2 <: GeoJSONFeature](resp: PaginatedResponse[T1]): PaginatedGeoJsonResponse[T2] = {
    PaginatedGeoJsonResponse[T2](
      resp.count,
      resp.hasPrevious,
      resp.hasNext,
      resp.page,
      resp.pageSize,
      ((resp.results: Seq[T1]) map { _.toGeoJSONFeature }),
      "FeatureCollection"
    )
  }

}

