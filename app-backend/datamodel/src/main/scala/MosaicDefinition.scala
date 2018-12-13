package com.rasterfoundry.datamodel

import com.rasterfoundry.bridge._
import geotrellis.vector.{MultiPolygon, Projected}
import io.circe.generic.JsonCodec

import java.util.UUID

@JsonCodec
final case class MosaicDefinition(sceneId: UUID,
                                  colorCorrections: ColorCorrect.Params,
                                  sceneType: Option[SceneType] = None,
                                  ingestLocation: Option[String],
                                  footprint: Option[MultiPolygon])

object MosaicDefinition {
  def fromScenesToProjects(
      scenesToProjects: Seq[SceneToProjectwithSceneType]
  ): Seq[MosaicDefinition] = {
    scenesToProjects.map {
      case SceneToProjectwithSceneType(
          sceneId,
          _,
          _,
          _,
          colorCorrection,
          sceneType,
          ingestLocation,
          footprint
          ) =>
        MosaicDefinition(sceneId,
                         colorCorrection,
                         sceneType,
                         ingestLocation,
                         footprint flatMap { _.geom.as[MultiPolygon] })
    }
  }

  def fromScenesToProjects(scenesToProjects: Seq[SceneToProjectwithSceneType],
                           redBand: Int,
                           greenBand: Int,
                           blueBand: Int): Seq[MosaicDefinition] = {
    scenesToProjects.map {
      case SceneToProjectwithSceneType(
          sceneId,
          _,
          _,
          _,
          colorCorrection,
          sceneType,
          ingestLocation,
          footprint
          ) => {
        val ccp = colorCorrection.copy(
          redBand = redBand,
          greenBand = greenBand,
          blueBand = blueBand
        )
        MosaicDefinition(sceneId,
                         ccp,
                         sceneType,
                         ingestLocation,
                         footprint flatMap { _.geom.as[MultiPolygon] })
      }
    }
  }
}
