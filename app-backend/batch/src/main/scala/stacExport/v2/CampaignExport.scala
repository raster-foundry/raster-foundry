package com.rasterfoundry.batch.stacExport.v2

import com.rasterfoundry.database.AnnotationLabelDao
import com.rasterfoundry.database.AnnotationProjectDao
import com.rasterfoundry.database.CampaignDao
import com.rasterfoundry.database.TaskDao
import com.rasterfoundry.datamodel.AnnotationProject
import com.rasterfoundry.datamodel.StacExport
import com.rasterfoundry.datamodel.UnionedGeomExtent
import com.rasterfoundry.notification.intercom.IntercomNotifier

import cats.data.StateT
import cats.effect.{Blocker, ContextShift, IO}
import cats.syntax.apply._
import cats.syntax.functor._
import cats.syntax.list._
import cats.syntax.traverse._
import com.azavea.stac4s._
import com.azavea.stac4s.extensions.label._
import com.azavea.stac4s.syntax._
import doobie.Transactor
import doobie.implicits._
import geotrellis.vector.Extent
import geotrellis.vector.MultiPolygon
import geotrellis.vector.Projected
import io.circe.Json
import io.circe.syntax._
import io.estatico.newtype.macros.newtype
import monocle.macros.GenLens

import java.time.Instant
import java.util.UUID
import better.files.File
import io.circe.Encoder
import io.circe.optics.JsonPath._

object newtypes {
  @newtype case class AnnotationProjectId(value: UUID)
  @newtype case class SceneItem(value: StacItem)
  @newtype case class LabelItem(value: StacItem)
  @newtype case class TaskGeoJSON(value: Json)
}

object optics {

  val remainingProjectsLens =
    GenLens[ExportState](_.remainingAnnotationProjects)

  val labelItemsLens =
    GenLens[ExportState](_.annotationProjectLabelItems)

  val sceneItemsLens =
    GenLens[ExportState](_.annotationProjectSceneItems)

  val labelAssetsLens =
    GenLens[ExportState](_.labelAssets)

  val itemCollectionLens =
    GenLens[StacItem](_.collection)

  val itemLinksLens =
    GenLens[StacItem](_.links)

  val itemAssetLens =
    GenLens[StacItem](_.assets)

  val assetHrefLens =
    GenLens[StacItemAsset](_.href)

  val catalogLinksLens =
    GenLens[StacCatalog](_.links)
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
) {

  val labelCollectionId = s"labels-${UUID.randomUUID}"
  val sceneCollectionId = s"scenes-${UUID.randomUUID}"

  private def encodableToFile[T: Encoder](
      value: T,
      file: File,
      relativePath: String
  )(implicit cs: ContextShift[IO]): IO[Unit] =
    fs2.Stream
      .emit(value.asJson.spaces2)
      .covary[IO]
      .through(
        fs2.text.utf8Encode[IO]
      )
      .through(
        fs2.io.file
          .writeAll[IO](
            file.path.resolve(relativePath),
            ExportData.fileBlocker
          )
      )
      .compile
      .drain

  private def writeCatalog(
      file: File
  )(implicit cs: ContextShift[IO]): IO[Unit] = {
    val collectionLinks = List(
      StacLink(
        "./images/collection.json",
        StacLinkType.Child,
        Some(`application/json`),
        None
      ),
      StacLink(
        "./labels/collection.json",
        StacLinkType.Child,
        Some(`application/json`),
        None
      )
    )
    val withLinks = optics.catalogLinksLens.modify(_ => collectionLinks)
    encodableToFile(withLinks(rootCatalog), file, "catalog.json")
  }

  private def writeImageryItems(
      file: File
  )(implicit cs: ContextShift[IO]): IO[Unit] = {
    val withCollection =
      optics.itemCollectionLens.modify(_ => Some(sceneCollectionId))
    val withParentLinks = optics.itemLinksLens.modify(
      links =>
        StacLink(
          "./collection.json",
          StacLinkType.Collection,
          Some(`application/json`),
          None
        ) +: StacLink(
          "../catalog.json",
          StacLinkType.StacRoot,
          Some(`application/json`),
          None
        ) +:
          links.filter(_.rel != StacLinkType.Collection)
    )
    (annotationProjectSceneItems.toList traverse {
      case (k, v) =>
        encodableToFile(
          (withCollection `compose` withParentLinks)(v.value),
          file,
          s"images/${k.value}.json"
        )
    }).void
  }

  private def fuseBboxes(bboxes: List[TwoDimBbox]): List[Bbox] =
    bboxes.toNel map { bboxesNel =>
      List(bboxesNel.tail.foldLeft(bboxesNel.head)((box1, box2) => {
        TwoDimBbox(
          List(box1.xmin, box2.xmin).min,
          List(box1.ymin, box2.ymin).min,
          List(box1.xmax, box2.xmax).max,
          List(box1.ymax, box2.ymax).max
        )
      }))
    } getOrElse Nil

  private def getTemporalExtent(items: List[StacItem]): Option[TemporalExtent] =
    items.toNel flatMap { itemsNel =>
      val datetimePath = root.datetime.as[Instant]
      val times = itemsNel map { item =>
        datetimePath.getOption(item.properties.asJson)
      }
      val minTime = times.toList.min
      val maxTime = times.toList.max
      (minTime, maxTime) mapN {
        case (start, end) => TemporalExtent(start, end)
      }
    }

  private def writeImageryCollection(
      file: File
  )(implicit cs: ContextShift[IO]): IO[Unit] = {
    val stacCollection = StacCollection(
      "1.0.0-beta2",
      Nil,
      sceneCollectionId,
      None,
      "Exported scenes from Groundwork",
      Nil,
      Proprietary(),
      Nil,
      StacExtent(
        SpatialExtent(
          fuseBboxes(annotationProjectSceneItems.values.toList map {
            _.value.bbox
          })
        ),
        getTemporalExtent(annotationProjectSceneItems.values.toList map {
          _.value
        }) map { te =>
          Interval(List(te))
        } getOrElse Interval(Nil)
      ),
      ().asJsonObject,
      ().asJsonObject,
      StacLink(
        "../catalog.json",
        StacLinkType.StacRoot,
        Some(`application/json`),
        None
      ) +: (annotationProjectSceneItems.values.toList map {
        (sceneItem: newtypes.SceneItem) =>
          StacLink(
            s"./${sceneItem.value.id}.json",
            StacLinkType.Item,
            Some(`application/json`),
            None
          )
      })
    )
    encodableToFile(stacCollection, file, "images/collection.json")
  }

  private def writeLabelAssets(
      file: File
  )(implicit cs: ContextShift[IO]): IO[Unit] =
    (labelAssets.toList traverse {
      case (k, v) =>
        encodableToFile(v.value, file, s"labels/data/${k.value}.geojson")
    }).void

  private def writeLabelCollection(
      file: File
  )(implicit cs: ContextShift[IO]): IO[Unit] = {
    val stacCollection = StacCollection(
      "1.0.0-beta2",
      Nil,
      labelCollectionId,
      None,
      "Exported labels from Groundwork",
      Nil,
      Proprietary(),
      Nil,
      StacExtent(
        SpatialExtent(
          fuseBboxes(annotationProjectLabelItems.values.toList map {
            _.value.bbox
          })
        ),
        getTemporalExtent(annotationProjectLabelItems.values.toList map {
          _.value
        }) map { te =>
          Interval(List(te))
        } getOrElse Interval(Nil)
      ),
      ().asJsonObject,
      ().asJsonObject,
      StacLink(
        "../catalog.json",
        StacLinkType.StacRoot,
        Some(`application/json`),
        None
      ) +: (annotationProjectLabelItems.values.toList map {
        (labelItem: newtypes.LabelItem) =>
          StacLink(
            s"./${labelItem.value.id}.json",
            StacLinkType.Item,
            Some(`application/json`),
            None
          )
      })
    )
    encodableToFile(stacCollection, file, "labels/collection.json")
  }

  private def writeLabelItems(
      file: File
  )(implicit cs: ContextShift[IO]): IO[Unit] = {
    val withCollection =
      optics.itemCollectionLens.modify(_ => Some(labelCollectionId))
    val withParentLinks = optics.itemLinksLens.modify(
      links =>
        StacLink(
          "./collection.json",
          StacLinkType.Collection,
          Some(`application/json`),
          None
        ) +: StacLink(
          "../catalog.json",
          StacLinkType.StacRoot,
          Some(`application/json`),
          None
        ) +:
          links.filter(_.rel != StacLinkType.Collection)
    )
    def withAsset(labelItem: StacItem) =
      optics.itemAssetLens.modify(
        assets =>
          assets ++ Map(
            "data" -> StacItemAsset(
              s"./data/${labelItem.id}",
              None,
              None,
              Set(StacAssetRole.Data),
              Some(`application/geo+json`)
            )
          )
      )(labelItem)
    (annotationProjectLabelItems.toList traverse {
      case (k, v) =>
        encodableToFile(
          (withParentLinks `compose` withCollection `compose` withAsset)(
            v.value
          ),
          file,
          s"labels/${k.value}.json"
        )
    }).void
  }

  def toFileSystem(rootDir: File)(implicit cs: ContextShift[IO]): IO[Unit] =
    writeImageryCollection(rootDir) *> writeImageryItems(rootDir) *> writeLabelCollection(
      rootDir
    ) *> writeLabelAssets(
      rootDir
    ) *> writeLabelItems(
      rootDir
    ) *> writeCatalog(
      rootDir
    )
}

object ExportData {
  val fileBlocker: Blocker = ???
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

  private def popAnnotationProject(
      projectId: newtypes.AnnotationProjectId
  ): ExportState => ExportState =
    optics.remainingProjectsLens.modify(_.filter(_.id != projectId.value))

  private def appendLabelItem(
      toAppend: Map[newtypes.AnnotationProjectId, newtypes.LabelItem]
  ): ExportState => ExportState =
    optics.labelItemsLens.modify(_ ++ toAppend)

  private def appendSceneItem(
      toAppend: Map[newtypes.AnnotationProjectId, newtypes.SceneItem]
  ): ExportState => ExportState =
    optics.sceneItemsLens.modify(_ ++ toAppend)

  private def appendLabelAssets(
      toAppend: Map[newtypes.AnnotationProjectId, newtypes.TaskGeoJSON]
  ): ExportState => ExportState =
    optics.labelAssetsLens.modify(_ ++ toAppend)

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
      sceneCreationTime = scene map { scene =>
        scene.filterFields.acquisitionDate getOrElse scene.createdAt
      }
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
