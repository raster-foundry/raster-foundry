package com.rasterfoundry.batch.stacExport.v2

import com.rasterfoundry.datamodel.StacExport
import com.rasterfoundry.notification.intercom.IntercomNotifier

import cats.effect.{ContextShift, IO}
import doobie.Transactor

import cats.syntax.apply._
import cats.syntax.list._
import cats.syntax.traverse._
import java.util.UUID
import com.azavea.stac4s._
import io.estatico.newtype.macros.newtype
import cats.data.StateT
import com.rasterfoundry.database.AnnotationProjectDao
import doobie.implicits._
import monocle.macros.GenLens
import com.rasterfoundry.database.TaskDao
import io.circe.Json
import com.rasterfoundry.database.AnnotationLabelDao
import com.rasterfoundry.datamodel.UnionedGeomExtent
import com.rasterfoundry.datamodel.AnnotationProject
import geotrellis.vector.Projected
import geotrellis.vector.MultiPolygon
import geotrellis.vector.Extent
import java.time.Instant
import io.circe.syntax._
import com.azavea.stac4s.extensions.label._
import com.azavea.stac4s.syntax._
import com.rasterfoundry.database.CampaignDao

object newtypes {
  @newtype case class AnnotationProjectId(value: UUID)
  @newtype case class SceneItem(value: StacItem)
  @newtype case class LabelItem(value: StacItem)
  @newtype case class TaskGeoJSON(value: Json)
}

case class ExportState(
    exportDefinition: StacExport,
    rootCatalog: StacCatalog,
    remainingAnnotationProjects: List[AnnotationProject],
    annotationProjectSceneItems: Map[
      newtypes.AnnotationProjectId,
      newtypes.SceneItem
    ],
    annotationProjectLabelItems: Map[
      newtypes.AnnotationProjectId,
      newtypes.LabelItem
    ],
    labelAssets: Map[
      newtypes.AnnotationProjectId,
      newtypes.TaskGeoJSON
    ]
)

case class ExportData private (
    rootCatalog: StacCatalog,
    annotationProjectSceneItems: Map[
      newtypes.AnnotationProjectId,
      newtypes.SceneItem
    ],
    annotationProjectLabelItems: Map[
      newtypes.AnnotationProjectId,
      newtypes.LabelItem
    ],
    labelAssets: Map[
      newtypes.AnnotationProjectId,
      newtypes.TaskGeoJSON
    ]
)

object ExportData {
  def fromExportState(state: ExportState): Option[ExportData] = {
    state.remainingAnnotationProjects.toNel.fold(
      Option(
        ExportData(
          state.rootCatalog,
          state.annotationProjectSceneItems,
          state.annotationProjectLabelItems,
          state.labelAssets
        )
      )
    )(_ => Option.empty[ExportData])
  }
}

class CampaignStacExport(
    campaignId: UUID,
    notifier: IntercomNotifier[IO],
    xa: Transactor[IO],
    exportDefinition: StacExport
)(
    implicit val
    cs: ContextShift[IO]
) {

  println(s"Look a notifier: $notifier")

  val runExport = StateT { step }

  def run(): IO[Unit] =
    for {
      campaign <- CampaignDao.getCampaignById(campaignId).transact(xa)
      childProjects <- campaign traverse { campaign =>
        AnnotationProjectDao.listByCampaign(campaign.id).transact(xa)
      }
      initialStateO = (campaign, childProjects) mapN {
        case (campaign, projects) =>
          val rootCatalog = StacCatalog(
            stacVersion,
            Nil,
            s"${exportDefinition.id}",
            None,
            s"Exported from Groundwork ${Instant.now()} for campaign ${campaign.name}",
            Nil
          )
          ExportState(
            exportDefinition,
            rootCatalog,
            projects,
            Map.empty,
            Map.empty,
            Map.empty
          )
      }
      assembled <- initialStateO traverse { init =>
        runExport.runS(init)
      }
    } yield
      (
        assembled flatMap { ExportData.fromExportState } map { coll =>
          println(s"Build up some data: $coll")
        } getOrElse { println(s"Well that went poorly") }
      )

  private val stacVersion = "1.0.0-beta2"

  private def step(from: ExportState): IO[(ExportState, Unit)] = {
    from.remainingAnnotationProjects match {
      case Nil    => IO.pure((from, ()))
      case h :: _ => processAnnotationProject(from, h) flatMap { step }
    }
  }

  private val remainingProjectsLens =
    GenLens[ExportState](_.remainingAnnotationProjects)

  private val labelItemsLens =
    GenLens[ExportState](_.annotationProjectLabelItems)

  private val sceneItemsLens =
    GenLens[ExportState](_.annotationProjectSceneItems)

  private val labelAssetsLens =
    GenLens[ExportState](_.labelAssets)

  private def popAnnotationProject(
      projectId: newtypes.AnnotationProjectId
  ): ExportState => ExportState =
    remainingProjectsLens.modify(_.filter(_.id != projectId.value))

  private def appendLabelItem(
      toAppend: Map[newtypes.AnnotationProjectId, newtypes.LabelItem]
  ): ExportState => ExportState =
    labelItemsLens.modify(_ ++ toAppend)

  private def appendSceneItem(
      toAppend: Map[newtypes.AnnotationProjectId, newtypes.SceneItem]
  ): ExportState => ExportState =
    sceneItemsLens.modify(_ ++ toAppend)

  private def appendLabelAssets(
      toAppend: Map[newtypes.AnnotationProjectId, newtypes.TaskGeoJSON]
  ): ExportState => ExportState =
    labelAssetsLens.modify(_ ++ toAppend)

  private def processAnnotationProject(
      inputState: ExportState,
      annotationProject: AnnotationProject
  ): IO[ExportState] = {
    for {
      // make the catalog for this annotation project
      // make the scene item for this annotation project with an s3 asset
      scene <- AnnotationProjectDao
        .getFirstScene(annotationProject.id)
        .transact(xa)
      s3ImageLocation = scene flatMap { _.ingestLocation }
      footprint = scene flatMap { _.dataFootprint }
      sceneCreationTime = scene map { _.createdAt }
      sceneItemsAppend = (s3ImageLocation, footprint, sceneCreationTime).mapN {
        case (url, footprint, timestamp) =>
          Map(
            newtypes.AnnotationProjectId(annotationProject.id) -> makeSceneItem(
              url,
              footprint,
              timestamp.toInstant,
              annotationProject
            )
          )
      } getOrElse Map.empty
      // make label asset
      featureGeoJSON <- AnnotationLabelDao
        .getAnnotationJsonByTaskStatus(
          annotationProject.id,
          inputState.exportDefinition.taskStatuses
        )
        .transact(xa)
      labelAssetAppend = featureGeoJSON map { geojson =>
        Map(
          newtypes.AnnotationProjectId(annotationProject.id) -> newtypes
            .TaskGeoJSON(geojson)
        )
      } getOrElse Map.empty
      // make the label item
      taskExtent <- TaskDao
        .createUnionedGeomExtent(
          annotationProject.id,
          inputState.exportDefinition.taskStatuses
        )
        .transact(xa)
      labelItemExtensionO <- AnnotationProjectDao
        .getAnnotationProjectStacInfo(annotationProject.id)
        .transact(xa)
      labelItemsAppend = (taskExtent, labelItemExtensionO) mapN {
        case (extent, labelItemExtension) =>
          Map(
            newtypes.AnnotationProjectId(annotationProject.id) -> makeLabelItem(
              extent,
              annotationProject,
              sceneCreationTime map { _.toInstant },
              labelItemExtension
            )
          )
      } getOrElse Map.empty
      // make the label collection for this annotation project with an s3 asset
    } yield {
      (appendLabelItem(labelItemsAppend) `compose` popAnnotationProject(
        newtypes.AnnotationProjectId(annotationProject.id)
      ) `compose` appendSceneItem(
        sceneItemsAppend
      ) `compose` appendLabelAssets(labelAssetAppend))(
        inputState
      )
    }

  }

  private def makeSceneItem(
      url: String,
      footprint: Projected[MultiPolygon],
      createdAt: Instant,
      annotationProject: AnnotationProject
  ): newtypes.SceneItem = {
    val latLngGeom = footprint.withSRID(4326)
    val latLngExtent = Extent(latLngGeom.geom.getEnvelopeInternal)
    newtypes.SceneItem(
      StacItem(
        s"${UUID.randomUUID}",
        stacVersion,
        Nil,
        "Feature",
        footprint.withSRID(4326),
        TwoDimBbox(
          latLngExtent.xmin,
          latLngExtent.ymin,
          latLngExtent.xmax,
          latLngExtent.ymax
        ),
        List(
          StacLink(
            "./collection.json",
            StacLinkType.Collection,
            Some(`application/json`),
            None
          ),
          StacLink(
            "../catalog.json",
            StacLinkType.StacRoot,
            Some(`application/json`),
            None
          )
        ),
        Map(
          "data" -> StacItemAsset(
            url,
            Some(s"COG for project ${annotationProject.name}"),
            None,
            Set(StacAssetRole.Data),
            Some(`image/cog`)
          )
        ),
        None,
        Map("datetime" -> createdAt.asJson).asJsonObject
      )
    )
  }

  private def makeLabelItem(
      extent: UnionedGeomExtent,
      annotationProject: AnnotationProject,
      datetime: Option[Instant],
      labelItemExtension: LabelItemExtension
  ): newtypes.LabelItem = {
    val itemId = UUID.randomUUID
    val latLngExtent = Extent(
      extent.geometry.withSRID(4326).geom.getEnvelopeInternal
    )
    newtypes.LabelItem(
      StacItem(
        s"$itemId",
        stacVersion,
        List("label"),
        "Feature",
        extent.geometry.withSRID(4326),
        TwoDimBbox(
          latLngExtent.xmin,
          latLngExtent.ymin,
          latLngExtent.xmax,
          latLngExtent.ymax
        ),
        List(
          StacLink(
            "./collection.json",
            StacLinkType.Collection,
            Some(`application/json`),
            None
          )
        ),
        Map(
          "data" -> StacItemAsset(
            s"./data/${itemId}.geojson",
            Some(s"Label data for ${annotationProject.name}"),
            None,
            Set(StacAssetRole.Data),
            Some(`application/geo+json`)
          )
        ),
        None,
        Map(
          "datetime" -> (datetime getOrElse Instant.now).asJson
        ).asJsonObject
      ).addExtensionFields(labelItemExtension)
    )
  }
}
