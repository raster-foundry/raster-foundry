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
  ExportAssetType,
  Scene,
  StacExport,
  TileLayer,
  UnionedGeomExtent
}

import better.files.File
import cats.data.{NonEmptyList, StateT}
import cats.effect.{Blocker, ContextShift, IO}
import cats.syntax.apply._
import cats.syntax.list._
import cats.syntax.traverse._
import com.azavea.stac4s._
import com.azavea.stac4s.extensions.label._
import com.azavea.stac4s.syntax._
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.typesafe.scalalogging.LazyLogging
import doobie.Transactor
import doobie.implicits._
import eu.timepit.refined.auto._
import geotrellis.vector.Extent
import io.circe.Encoder
import io.circe.optics.JsonPath._
import io.circe.syntax._
import monocle.macros.GenLens

import scala.concurrent.ExecutionContext

import java.net.URI
import java.time.{Duration, Instant}
import java.util.UUID
import java.util.concurrent.Executors

object optics {

  val remainingProjectsLens =
    GenLens[ExportState](_.remainingAnnotationProjects)

  val labelItemsLens =
    GenLens[ExportState](_.annotationProjectLabelItems)

  val imageryItemsLens =
    GenLens[ExportState](_.annotationProjectImageryItems)

  val labelAssetsLens =
    GenLens[ExportState](_.labelAssets)

  val imageryS3LinksLens =
    GenLens[ExportState](_.imageryS3Links)

  val itemCollectionLens =
    GenLens[StacItem](_.collection)

  val itemLinksLens =
    GenLens[StacItem](_.links)

  val itemAssetLens =
    GenLens[StacItem](_.assets)

  val assetHrefLens =
    GenLens[StacAsset](_.href)

  val catalogLinksLens =
    GenLens[StacCatalog](_.links)
}

object AssetTypesKey {
  val cog = "cog"
  val signedURL = "signedURL"
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
    ],
    imageryS3Links: Map[
      newtypes.AnnotationProjectId,
      newtypes.S3URL
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
    ],
    readme: String,
    imageryS3Links: Map[
      newtypes.AnnotationProjectId,
      newtypes.S3URL
    ]
) extends LazyLogging {

  val labelCollectionId = s"labels-${UUID.randomUUID}"
  val imageryCollectionId = s"scenes-${UUID.randomUUID}"
  val s3Client = S3()

  private def stringToFile(
      value: String,
      file: File,
      relativePath: String
  )(implicit cs: ContextShift[IO]): IO[Unit] = {
    (IO(logger.info(s"Writing text file to $relativePath")) *> IO {
      file.path.resolve(relativePath)
    } map { (absPath: java.nio.file.Path) =>
      val parent = absPath.getParent
      File(parent).createIfNotExists(true, true)
    }) *>
      fs2.Stream
        .emit(value)
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

  private def encodableToFile[T: Encoder](
      value: T,
      file: File,
      relativePath: String
  )(implicit cs: ContextShift[IO]): IO[Unit] =
    stringToFile(value.asJson.spaces2, file, relativePath)

  private def writeCOGToFile(
      uri: URI,
      file: File,
      relativePath: String
  )(implicit cs: ContextShift[IO]): IO[Unit] = {
    for {
      _ <- IO(logger.info(s"Writing COG at $uri to $relativePath"))
      s3Object <- IO { s3Client.getObject(uri) }
      _ <- (IO { file.path.resolve(relativePath) } map {
        (absPath: java.nio.file.Path) =>
          val parent = absPath.getParent
          File(parent).createIfNotExists(true, true)
      }) *>
        fs2.io
          .readInputStream(
            IO(s3Object.getObjectContent().getDelegateStream),
            // Chunk size is set to default used for S3 CLI multipart (8MB)
            // See: https://docs.aws.amazon.com/cli/latest/topic/s3-config.html#multipart-chunksize
            1024 * 8,
            ExportData.fileBlocker
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
    } yield ()
  }

  private def writeReadme(
      file: File
  )(implicit cs: ContextShift[IO]): IO[Unit] =
    stringToFile(readme, file, "README.md")

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
          "../collection.json",
          StacLinkType.Collection,
          Some(`application/json`),
          None
        ) +: StacLink(
          "../../catalog.json",
          StacLinkType.StacRoot,
          Some(`application/json`),
          None
        ) +:
          links.filter(link =>
          !Set[StacLinkType](StacLinkType.Collection, StacLinkType.StacRoot)
            .contains(link.rel)))
    (annotationProjectImageryItems.toList traverse {
      case (_, sceneItem) =>
        imageryS3Links.get(
          newtypes.AnnotationProjectId(UUID.fromString(sceneItem.value.id))
        ) match {
          case Some(s3Link) =>
            val ingestLocation = s3Link.value
            writeCOGToFile(
              URI.create(ingestLocation),
              file,
              s"images/${sceneItem.value.id}/${new java.io.File(ingestLocation).getName}"
            ) *>
              encodableToFile(
                (withCollection `compose` withParentLinks)(sceneItem.value),
                file,
                s"images/${sceneItem.value.id}/item.json"
              )
          case _ =>
            encodableToFile(
              (withCollection `compose` withParentLinks)(sceneItem.value),
              file,
              s"images/${sceneItem.value.id}/item.json"
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
      "Collection",
      "1.0.0",
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
      Map.empty,
      ().asJsonObject,
      StacLink(
        "../catalog.json",
        StacLinkType.StacRoot,
        Some(`application/json`),
        None
      ) +: (annotationProjectImageryItems.values.toList map {
        (sceneItem: newtypes.SceneItem) =>
          StacLink(
            s"./${sceneItem.value.id}/item.json",
            StacLinkType.Item,
            Some(`application/json`),
            None
          )
      }),
      None
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
      "Collection",
      "1.0.0",
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
      Map.empty,
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
      }),
      None
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
            "data" -> StacAsset(
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
    ) *> writeReadme(rootDir)
  }
}

object ExportData {
  private val fileIO = ExecutionContext.fromExecutor(
    Executors.newCachedThreadPool(
      new ThreadFactoryBuilder().setNameFormat("export-file-io-%d").build()
    )
  )
  val fileBlocker: Blocker = Blocker.liftExecutionContext(fileIO)

  def fromExportState(
      state: ExportState,
      readme: String
  ): Option[ExportData] = {
    state.remainingAnnotationProjects.toNel.fold(
      Option(
        ExportData(
          state.rootCatalog,
          state.annotationProjectImageryItems,
          state.annotationProjectLabelItems,
          state.labelAssets,
          readme,
          state.imageryS3Links
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
  cs: ContextShift[IO])
    extends LazyLogging {
  val s3Client = S3()

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
            "Catalog",
            "1.0.0",
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
            Map.empty,
            Map.empty
          )
      }
      assembled <- initialStateO traverse { init =>
        runExport.runS(init)
      }
      readme = (childProjects, assembled) mapN {
        case (projList, state) =>
          README.render(
            projList,
            state.annotationProjectLabelItems,
            state.annotationProjectImageryItems,
            state.exportDefinition.exportAssetTypes
          )
      }
    } yield {
      (assembled, readme).tupled flatMap {
        case (state, doc) => ExportData.fromExportState(state, doc)
      }
    }

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

  private def appendImageS3Links(
      toAppend: Map[newtypes.AnnotationProjectId, newtypes.S3URL]
  ): ExportState => ExportState =
    optics.imageryS3LinksLens.modify(_ ++ toAppend)

  private def includeAssetTypeInExport(
      exportAssetTypes: Option[NonEmptyList[ExportAssetType]],
      assetType: ExportAssetType
  ): Boolean =
    exportAssetTypes map { assetTypes =>
      assetTypes.toList.contains(assetType)
    } getOrElse (false)

  private def exportAssetsAndLinks(
      maybeScene: Option[Scene],
      tileLayers: List[TileLayer],
      exportAssetTypes: Option[NonEmptyList[ExportAssetType]],
      annotationProjectId: UUID
  ): IO[
    (Map[String, StacAsset], Map[newtypes.AnnotationProjectId, newtypes.S3URL])
  ] = {
    val maybeIngestLocation = maybeScene flatMap { _.ingestLocation }
    val signedUrlDurationInDays = 7
    for {
      maybeSignedURLAsset: Option[(String, StacAsset)] <- maybeIngestLocation match {
        case Some(ingestLocation)
            if includeAssetTypeInExport(
              exportAssetTypes,
              ExportAssetType.SignedURL
            ) =>
          IO {
            s3Client.signUri(
              ingestLocation,
              duration = Duration.ofDays(signedUrlDurationInDays)
            )
          } map { signedUrl =>
            Some(
              (
                AssetTypesKey.signedURL,
                StacAsset(
                  signedUrl,
                  Some(
                    "Image download URL"
                  ), // The displayed title for clients and users
                  Some(
                    s"Signed URL (expires ${java.time.LocalDateTime.now().plusDays(signedUrlDurationInDays)})"
                  ),
                  Set(StacAssetRole.Data),
                  Some(`image/cog`)
                )
              )
            )
          }
        case _ =>
          IO.pure(None)
      }
      maybeCOGAssetAndS3Link: Option[
        ((String, StacAsset), (newtypes.AnnotationProjectId, newtypes.S3URL))
      ] <- maybeIngestLocation match {
        case Some(ingestLocation)
            if includeAssetTypeInExport(
              exportAssetTypes,
              ExportAssetType.COG
            ) =>
          IO.pure(
            Some(
              (
                (
                  (
                    AssetTypesKey.cog,
                    StacAsset(
                      s"./${new java.io.File(ingestLocation).getName}", // relative path to COG
                      Some(
                        "Image local relative path"
                      ), // The displayed title for clients and users
                      Some("COG"),
                      Set(StacAssetRole.Data),
                      Some(`image/cog`)
                    )
                  )
                ),
                (
                  newtypes.AnnotationProjectId(annotationProjectId),
                  newtypes.S3URL(ingestLocation)
                )
              )
            )
          )
        case _ =>
          IO.pure(None)
      }
      tileLayersAssets: List[(String, StacAsset)] = tileLayers map { layer =>
        (
          layer.name,
          StacAsset(
            layer.url,
            Some("Image layer"), // The displayed title for clients and users
            Some(s"${layer.layerType} tiles"),
            Set(StacAssetRole.Data),
            layer.layerType match {
              case MVT =>
                Some(VendorMediaType("application/vnd.mapbox-vector-tile"))
              case TMS => Some(`image/png`)
            }
          )
        )
      }
      assets: Map[String, StacAsset] = Map(
        tileLayersAssets ++
          List(
            maybeSignedURLAsset,
            maybeCOGAssetAndS3Link flatMap {
              case ((maybeCog, _)) => Some(maybeCog)
              case _               => None
            }
          ).flatten: _*
      )
      s3Links: Map[newtypes.AnnotationProjectId, newtypes.S3URL] = maybeCOGAssetAndS3Link
        .map({ pair =>
          Map(pair._2)
        })
        .getOrElse(Map.empty)
    } yield (assets, s3Links)
  }

  private def imageryItemFromTileLayers(
      annotationProject: AnnotationProject,
      exportAssetTypes: Option[NonEmptyList[ExportAssetType]],
      taskStatuses: List[String]
  ): IO[
    (
        Option[newtypes.SceneItem],
        Map[newtypes.AnnotationProjectId, newtypes.S3URL]
    )
  ] = {
    for {
      _ <- IO(logger.info(s"Building imagery item from tile layers"))
      maybeScene <- AnnotationProjectDao
        .getFirstScene(annotationProject.id)
        .transact(xa)
      _ <- IO {
        logger.debug(maybeScene match {
          case Some(scene) => s"Found scene with id ${scene.id}"
          case _           => "No scene found"
        })
      }
      tileLayers <- TileLayerDao
        .listByProjectId(annotationProject.id)
        .transact(xa)
      _ <- IO {
        logger.debug(
          s"Found ${tileLayers.size} tile layers"
        )
      }
      extentO <- TaskDao
        .createUnionedGeomExtent(annotationProject.id, taskStatuses)
        .transact(xa)
      (assets, link) <- exportAssetsAndLinks(
        maybeScene,
        tileLayers,
        exportAssetTypes,
        annotationProject.id
      )
      item = extentO map { unionedGeom =>
        makeTileLayersItem(
          annotationProject.id,
          assets,
          unionedGeom.geometry.geom.getEnvelopeInternal,
          Instant.now
        )
      }
      _ <- IO {
        logger.debug(
          s"Found assets $assets"
        )
      }
      _ <- IO {
        logger.debug(
          s"Returning STAC item $item"
        )
      }
    } yield (item, link)
  }

  private def processAnnotationProject(
      inputState: ExportState,
      annotationProject: AnnotationProject
  ): IO[ExportState] = {
    for {
      // make the catalog for this annotation project
      // make the scene item for this annotation project with a tile layer asset
      (imageryItemO, imageS3Link) <- imageryItemFromTileLayers(
        annotationProject,
        inputState.exportDefinition.exportAssetTypes,
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
      ) `compose` appendLabelAssets(
        labelAssetAppend
      ) `compose` appendImageS3Links(imageS3Link))(
        inputState
      )
    }

  }

  private def makeTileLayersItem(
      annotationProjectId: UUID,
      assets: Map[String, StacAsset],
      extent: Extent,
      createdAt: Instant
  ): newtypes.SceneItem = {
    newtypes.SceneItem(
      StacItem(
        annotationProjectId.toString,
        "1.0.0",
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
        ItemProperties(ItemDatetime.PointInTime(createdAt))
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
        "1.0.0",
        List(
          "https://raw.githubusercontent.com/stac-extensions/label/v1.0.0/json-schema/schema.json"
        ),
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
            s"../images/${sceneItem.value.id}/item.json",
            StacLinkType.Source,
            Some(`application/json`),
            None
          )
        ),
        Map.empty,
        None,
        ItemProperties(ItemDatetime.PointInTime(datetime getOrElse Instant.now))
      ).addExtensionFields(labelItemExtension)
    )
  }
}
