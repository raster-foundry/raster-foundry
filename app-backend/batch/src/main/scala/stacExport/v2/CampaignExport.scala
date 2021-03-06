package com.rasterfoundry.batch.stacExport.v2

import com.rasterfoundry.common.S3
import com.rasterfoundry.database.{
  AnnotationLabelClassGroupDao,
  AnnotationLabelDao,
  AnnotationProjectDao,
  CampaignDao,
  TaskDao,
  TileLayerDao
}
import com.rasterfoundry.datamodel.TileLayerType.{MVT, TMS}
import com.rasterfoundry.datamodel.{
  AnnotationLabelClassGroup,
  AnnotationProject,
  StacExport,
  TileLayer,
  UnionedGeomExtent
}

import better.files.File
import cats.data.StateT
import cats.effect.{Blocker, ContextShift, IO}
import cats.syntax.apply._
import cats.syntax.list._
import cats.syntax.traverse._
import com.azavea.stac4s._
import com.azavea.stac4s.extensions.label._
import com.azavea.stac4s.syntax._
import com.google.common.util.concurrent.ThreadFactoryBuilder
import doobie.Transactor
import doobie.implicits._
import geotrellis.vector.Extent
import io.circe.optics.JsonPath._
import io.circe.syntax._
import io.circe.{Encoder, Json}
import io.estatico.newtype.macros.newtype
import monocle.macros.GenLens

import scala.concurrent.ExecutionContext

import java.time.{Duration, Instant}
import java.util.UUID
import java.util.concurrent.Executors

@SuppressWarnings(Array("AsInstanceOf"))
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

  val imageryItemsLens =
    GenLens[ExportState](_.annotationProjectImageryItems)

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
    labelGroupOpt: Option[List[AnnotationLabelClassGroup]],
    rootCatalog: StacCatalog,
    remainingAnnotationProjects: List[AnnotationProject],
    annotationProjectImageryItems: Map[
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
    annotationProjectImageryItems: Map[
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
  val imageryCollectionId = s"scenes-${UUID.randomUUID}"
  val s3Client = S3()

  private def encodableToFile[T: Encoder](
      value: T,
      file: File,
      relativePath: String
  )(implicit cs: ContextShift[IO]): IO[Unit] = {
    (IO { file.path.resolve(relativePath) } map {
      (absPath: java.nio.file.Path) =>
        val parent = absPath.getParent
        File(parent).createIfNotExists(true, true)
    }) *>
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
  }

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
      optics.itemCollectionLens.modify(_ => Some(imageryCollectionId))
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
          links.filter(link =>
          !Set[StacLinkType](StacLinkType.Collection, StacLinkType.StacRoot)
            .contains(link.rel)))

    def withAsset(stacItem: StacItem): IO[StacItem] = {
      stacItem.assets.get("data") match {
        case Some(asset) =>
          IO {
            s3Client.maybeSignUri(asset.href, duration = Duration.ofDays(7))
          } map { signedUrl =>
            (optics.itemAssetLens.modify(
              assets =>
                assets ++ Map(
                  "data" -> optics.assetHrefLens
                    .modify(_ => signedUrl)(asset)
              )))(stacItem)
          }
        case None =>
          IO.pure(stacItem)
      }
    }
    (annotationProjectImageryItems.toList traverse {
      case (_, v) =>
        withAsset(v.value) flatMap { (item: StacItem) =>
          encodableToFile(
            (withCollection `compose` withParentLinks)(item),
            file,
            s"images/${item.id}.json"
          )
        }
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
      imageryCollectionId,
      None,
      "Exported scenes from Groundwork",
      Nil,
      Proprietary(),
      Nil,
      StacExtent(
        SpatialExtent(
          fuseBboxes(annotationProjectImageryItems.values.toList map {
            _.value.bbox
          })
        ),
        getTemporalExtent(annotationProjectImageryItems.values.toList map {
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
      ) +: (annotationProjectImageryItems.values.toList map {
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
        annotationProjectLabelItems.get(k) traverse { labelItem =>
          encodableToFile(
            v.value,
            file,
            s"labels/data/${labelItem.value.id}.geojson"
          )
        }
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
          links.filter(link =>
          !Set[StacLinkType](StacLinkType.Collection, StacLinkType.StacRoot)
            .contains(link.rel)))
    def withAsset(labelItem: StacItem) =
      optics.itemAssetLens.modify(
        assets =>
          assets ++ Map(
            "data" -> StacItemAsset(
              s"./data/${labelItem.id}.geojson",
              None,
              None,
              Set(StacAssetRole.Data),
              Some(`application/geo+json`)
            )
        ))(labelItem)
    (annotationProjectLabelItems.toList traverse {
      case (_, v) =>
        encodableToFile(
          (withParentLinks `compose` withCollection `compose` withAsset)(
            v.value
          ),
          file,
          s"labels/${v.value.id}.json"
        )
    }).void
  }

  def toFileSystem(rootDir: File)(implicit cs: ContextShift[IO]): IO[Unit] = {
    writeImageryCollection(rootDir) *> writeImageryItems(rootDir) *>
      writeLabelCollection(
        rootDir
      ) *> writeLabelAssets(
      rootDir
    ) *> writeLabelItems(
      rootDir
    ) *> writeCatalog(
      rootDir
    )
  }
}

object ExportData {
  private val fileIO = ExecutionContext.fromExecutor(
    Executors.newCachedThreadPool(
      new ThreadFactoryBuilder().setNameFormat("export-file-io-%d").build()
    )
  )
  val fileBlocker: Blocker = Blocker.liftExecutionContext(fileIO)

  def fromExportState(state: ExportState): Option[ExportData] = {
    state.remainingAnnotationProjects.toNel.fold(
      Option(
        ExportData(
          state.rootCatalog,
          state.annotationProjectImageryItems,
          state.annotationProjectLabelItems,
          state.labelAssets
        )
      )
    )(_ => Option.empty[ExportData])
  }
}

class CampaignStacExport(
    campaignId: UUID,
    xa: Transactor[IO],
    exportDefinition: StacExport
)(implicit
  val
  cs: ContextShift[IO]) {

  val runExport = StateT { step }

  def run(): IO[Option[ExportData]] =
    for {
      campaignOpt <- CampaignDao.getCampaignById(campaignId).transact(xa)
      labelGroupOpt <- campaignOpt traverse { campaign =>
        AnnotationLabelClassGroupDao.listByCampaignId(campaign.id).transact(xa)
      }
      childProjects <- campaignOpt traverse { campaign =>
        AnnotationProjectDao.listByCampaign(campaign.id).transact(xa)
      }
      initialStateO = (campaignOpt, childProjects) mapN {
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
            labelGroupOpt,
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
    } yield {
      assembled flatMap { ExportData.fromExportState }
    }

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

  private def appendImageryItem(
      toAppend: Map[newtypes.AnnotationProjectId, newtypes.SceneItem]
  ): ExportState => ExportState =
    optics.imageryItemsLens.modify(_ ++ toAppend)

  private def appendLabelAssets(
      toAppend: Map[newtypes.AnnotationProjectId, newtypes.TaskGeoJSON]
  ): ExportState => ExportState =
    optics.labelAssetsLens.modify(_ ++ toAppend)

  private def imageryItemFromTileLayers(
      annotationProject: AnnotationProject,
      taskStatuses: List[String]
  ): IO[Option[newtypes.SceneItem]] =
    for {
      tileLayers <- TileLayerDao
        .listByProjectId(annotationProject.id)
        .transact(xa)
      extentO <- TaskDao
        .createUnionedGeomExtent(annotationProject.id, taskStatuses)
        .transact(xa)
      item = extentO map { unionedGeom =>
        makeTileLayersItem(
          tileLayers,
          unionedGeom.geometry.geom.getEnvelopeInternal,
          Instant.now
        )
      }
    } yield item

  private def processAnnotationProject(
      inputState: ExportState,
      annotationProject: AnnotationProject
  ): IO[ExportState] = {
    for {
      // make the catalog for this annotation project
      // make the scene item for this annotation project with a tile layer asset
      imageryItemO <- imageryItemFromTileLayers(
        annotationProject,
        inputState.exportDefinition.taskStatuses
      )
      imageryItemsAppend = imageryItemO map { (item: newtypes.SceneItem) =>
        Map(newtypes.AnnotationProjectId(annotationProject.id) -> item)
      } getOrElse Map.empty
      // make label asset
      featureGeoJSON <- AnnotationLabelDao
        .getAnnotationJsonByTaskStatus(
          annotationProject.id,
          inputState.exportDefinition.taskStatuses,
          inputState.labelGroupOpt
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
        .getAnnotationProjectStacInfo(
          annotationProject.id,
          inputState.labelGroupOpt
        )
        .transact(xa)
      labelItemsAppend = (taskExtent, labelItemExtensionO, imageryItemO) mapN {
        case (extent, labelItemExtension, imageryItem) =>
          Map(
            newtypes.AnnotationProjectId(annotationProject.id) -> makeLabelItem(
              extent,
              root.datetime
                .as[Instant]
                .getOption(imageryItem.value.properties.asJson) orElse Some(
                Instant.now
              ),
              labelItemExtension,
              imageryItem
            )
          )
      } getOrElse Map.empty
      // make the label collection for this annotation project with an s3 asset
    } yield {
      (appendLabelItem(labelItemsAppend) `compose` popAnnotationProject(
        newtypes.AnnotationProjectId(annotationProject.id)
      ) `compose` appendImageryItem(
        imageryItemsAppend
      ) `compose` appendLabelAssets(labelAssetAppend))(
        inputState
      )
    }

  }

  private def makeTileLayersItem(
      tileLayers: List[TileLayer],
      extent: Extent,
      createdAt: Instant
  ): newtypes.SceneItem = {
    val assets = Map(tileLayers map { layer =>
      layer.name -> StacItemAsset(
        layer.url,
        Some(layer.name),
        Some(s"${layer.layerType} tiles"),
        Set(StacAssetRole.Data),
        layer.layerType match {
          case MVT =>
            Some(VendorMediaType("application/vnd.mapbox-vector-tile"))
          case TMS => Some(`image/png`)
        }
      )
    }: _*)
    newtypes.SceneItem(
      StacItem(
        s"${UUID.randomUUID}",
        stacVersion,
        Nil,
        "Feature",
        extent.toPolygon,
        TwoDimBbox(
          extent.xmin,
          extent.ymin,
          extent.xmax,
          extent.ymax
        ),
        Nil,
        assets,
        None,
        Map("datetime" -> createdAt.asJson).asJsonObject
      )
    )
  }

  private def makeLabelItem(
      extent: UnionedGeomExtent,
      datetime: Option[Instant],
      labelItemExtension: LabelItemExtension,
      sceneItem: newtypes.SceneItem
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
            s"../images/${sceneItem.value.id}.json",
            StacLinkType.Source,
            Some(`application/json`),
            None
          )
        ),
        Map.empty,
        None,
        Map(
          "datetime" -> (datetime getOrElse Instant.now).asJson
        ).asJsonObject
      ).addExtensionFields(labelItemExtension)
    )
  }
}
