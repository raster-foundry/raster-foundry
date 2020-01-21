package com.rasterfoundry.batch.projectLiberation

import com.rasterfoundry.batch.Job
import com.rasterfoundry.datamodel._
import com.rasterfoundry.database.ProjectDao
import com.rasterfoundry.database.Implicits._

import cats.data.EitherT
import cats.effect.IO
import cats.implicits._
import doobie.{ConnectionIO, Fragment}
import doobie.implicits._
import doobie.postgres.implicits._
import geotrellis.vector.{Geometry, Projected}
import io.circe.Json
import io.circe.optics.JsonPath._
import io.circe.syntax._
import shapeless._

import scala.util.Try
import java.net.URI
import java.util.UUID

sealed abstract class FailureStage extends Throwable
case object GuessTaskSize extends FailureStage
case object CreateAnnotationProject extends FailureStage
case object CreateProjectTiles extends FailureStage
case object CreateLabelClassGroups extends FailureStage
case object CreateLabelClasses extends FailureStage
case object CreateLabels extends FailureStage
case object NukeStaleData extends FailureStage

sealed abstract class AnnotationProjectType(val repr: String) {
  override def toString = repr
}
case object Detection extends AnnotationProjectType("DETECTION")
case object Classification extends AnnotationProjectType("CLASSIFICATION")
case object Segmentation extends AnnotationProjectType("SEGMENTATION")

object AnnotationProjectType {
  def fromStringO(s: String): Option[AnnotationProjectType] =
    s.toLowerCase match {
      case "classification" => Some(Classification)
      case "detection"      => Some(Detection)
      case "segmentation"   => Some(Segmentation)
      case _                => None
    }
}

class ProjectLiberation(tileHost: URI) {

  // using only simple types so I don't have to write metas for custom types --
  // that work can happen when we create Daos / proper datamodels for these things
  type AnnotationProject =
    String :: String :: String :: Int :: Option[
      Projected[Geometry]
    ] :: Option[UUID] :: Option[UUID] :: Option[UUID] :: HNil

  object AnnotationProject {
    // need an apply method for mapN in projectToAnnotationProject -- this pattern will
    // be repeated and is why I'm picking HLists over a big tuple here (type alias + apply for tuple
    // seemed goofy in a way that this... doesn't. That's pretty subjective and even typing it out
    // I'm less sure of it that I was 20 seconds ago).
    // the other benefit is getting to enforce non-optionality in apply where the hlist can still
    // hold options
    def apply(
        createdBy: String,
        name: String,
        projectType: AnnotationProjectType,
        taskSizeMeters: Int,
        aoi: Projected[Geometry],
        labelersId: UUID,
        validatorsId: UUID,
        projectId: UUID
    ): AnnotationProject =
      createdBy :: name :: projectType.repr :: taskSizeMeters :: Option(aoi) :: Option(
        labelersId
      ) :: Option(validatorsId) :: Option(projectId) :: HNil
  }

  def projectToAnnotationProject(
      project: Project,
      extras: Json,
      taskSizeMeters: Int
  ): Either[FailureStage, AnnotationProject] = {
    val annotatePartial = root.extras.annotate
    val projectTypeLens = annotatePartial.projectType.string
    val labelersLens = annotatePartial.labelers.string
    val validatorsLens = annotatePartial.validators.string
    val aoiLens = annotatePartial.aoi.geometry.json

    Either.fromOption(
      (
        Option(project.createdBy),
        Option(project.name),
        projectTypeLens.getOption(extras) flatMap {
          AnnotationProjectType.fromStringO _
        },
        Option(taskSizeMeters),
        // this succeeds, even if we have a bad aoi or no aoi,
        aoiLens
          .getOption(extras) flatMap { _.as[Projected[Geometry]].toOption } orElse {
          project.extent
        },
        labelersLens.getOption(extras) flatMap { s =>
          Try { UUID.fromString(s) } toOption
        },
        validatorsLens.getOption(extras) flatMap { s =>
          Try { UUID.fromString(s) } toOption
        },
        Option(project.id)
      ).mapN(
        AnnotationProject.apply _
      ),
      CreateAnnotationProject
    )
  }

  private def getAnnotationProjects: ConnectionIO[List[Project]] =
    ProjectDao.query
      .filter(
        Fragment.const("""tags @> '{ "annotate" }'""")
      )
      .list

  private def getProjectTaskSizeMeters(
      project: Project
  ): ConnectionIO[Either[FailureStage, Int]] = ???

  // make an annotation project from the existing project
  private def createAnnotationProject(
      project: Project,
      extras: Json
  ): ConnectionIO[Either[FailureStage, UUID]] = {
    val converted = projectToAnnotationProject(project, extras, ???)
    converted traverse {
      case createdBy :: name :: projectType :: taskSizeMeters :: aoi :: labelersId :: validatorsId :: projectId :: HNil =>
        fr"""
      insert into annotation_projects (
        uuid_generate_v4(),
        now(),
        ${createdBy},
        $name,
        $projectType,
        $taskSizeMeters,
        $aoi,
        $labelersId,
        $validatorsId,
        $projectId
      );
      """.update.withUniqueGeneratedKeys[UUID]("id")
    }

  }

  // create tiles entries for tms layer
  private def createProjectTiles(
      project: Project,
      annotationProjectId: UUID
  ): ConnectionIO[Either[FailureStage, Unit]] = {
    // i tried .adaptError(_ => CreateProjectTiles: FailureStage) instead of
    // the tortured either handling that this wound up with, but adaptError
    // is invariant in its type parameters
    fr"""
      INSERT INTO tiles (
        uuid_generate_v4(),
        project.name,
        s"$tileHost/${project.id}/{z}/{x}/{y}",
        true,
        false,
        'TMS',
        $annotationProjectId
      );
    """.update.run.attempt map { result =>
      result.bimap(_ => CreateProjectTiles: FailureStage, _ => ())
    }
  }

  private def createLabelGroups(
      extras: Json,
      annotationProjectId: UUID
  ): ConnectionIO[Either[FailureStage, List[UUID]]] = {
    val groupsLens = root.annotate.labelGroups.json

    groupsLens.getOption(extras) flatMap { _.as[Map[UUID, String]].toOption } flatMap {
      groupsMap =>
        {
          val records = groupsMap.zipWithIndex map {
            case ((groupId, groupName), n) =>
              Fragment.const(
                s"($groupId, $groupName, $annotationProjectId, $n)"
              )
          }
          records.toList.toNel
        }
    } traverse {
      case recordsNel =>
        Fragment.const(s"""
            INSERT INTO annotation_label_class_groups (
              id,
              name,
              annotation_project_id,
              idx
            ) VALUES ${recordsNel.intercalate(fr",")};
          """).update.withGeneratedKeys[UUID]("id").compile.to[List]
    }
  } map { opt =>
    Either.fromOption(opt, CreateLabelClassGroups)
  }

  private def getLabelClassInsertFragment(
      labelClassJson: Json,
      idx: Int,
      annotationLabelGroupIds: Set[UUID]
  ): Either[FailureStage, Fragment] = {
    // why lenses instead of just decoding to a case class?
    // I have no idea what the evolution of the extras field looked like.
    // my assumption is that it changed over time in ways that I won't be able
    // to figure out from looking at examples, and I don't know what fields are and
    // aren't necessary. Optics here aren't really different from having a cursor-based
    // decoder that tolerates missing fields and keeps access / parsing closer to where
    // I use the data than a case class up top would.
    val idLens = root.id.string
    val nameLens = root.name.string
    val defaultLens = root.default.boolean
    val labelGroupLens = root.labelGroup.string
    val determinantLens = root.determinant.boolean
    val hexCodeLens = root.colorHexCode.string

    (
      idLens.getOption(labelClassJson),
      nameLens.getOption(labelClassJson),
      defaultLens.getOption(labelClassJson) orElse { Some(false) },
      labelGroupLens.getOption(labelClassJson),
      determinantLens.getOption(labelClassJson) orElse { Some(false) },
      hexCodeLens.getOption(labelClassJson)
    ).mapN {
      case (
          idString,
          name,
          default,
          labelGroupIdString,
          determinant,
          hexCode
          ) =>
        // leftMap necessary because uuid from string can fail,
        // in which case we get a generic throwable
        Either
          .fromTry(
            Try {
              val labelGroupId = UUID.fromString(labelGroupIdString)
              val id = UUID.fromString(idString)
              if (annotationLabelGroupIds.contains(labelGroupId)) {
                fr"($id, $name, $labelGroupId, $hexCode, $default, $determinant, $idx)"
              } else {
                throw CreateLabelClasses
              }
            }
          )
          .leftMap { _ =>
            CreateLabelClasses
          }
    } getOrElse { Left(CreateLabelClasses) }
  }

  // maybe (UUID, name)?
  private def createLabelClasses(
      extras: Json,
      annotationLabelGroupIds: Set[UUID]
  ): ConnectionIO[Either[FailureStage, List[UUID]]] = {
    val labelClassesLens = root.annotate.labels.each.json
    val fragmentsE = labelClassesLens
      .getAll(extras)
      .zipWithIndex traverse {
      case (json, idx) =>
        getLabelClassInsertFragment(
          json,
          idx,
          annotationLabelGroupIds
        )
    } map { _.toNel }
    fragmentsE match {
      case Right(Some(recordsNel)) =>
        Fragment
          .const(s"""
            INSERT INTO annotation_label_classes (
              id,
              name,
              annotation_label_group_id,
              color_hex_code,
              is_default,
              is_determinant,
              idx
            ) VALUES ${recordsNel.intercalate(fr",")};
          """)
          .update
          .withGeneratedKeys[UUID]("id")
          .compile
          .to[List]
          .attempt
          .map { result =>
            result.leftMap { _ =>
              CreateLabelClasses
            }
          }
      case _ =>
        // truly astounding sometimes just how little the Scala compiler is able
        // (or willing) to infer.
        Either
          .left[FailureStage, List[UUID]](CreateLabelClasses)
          .pure[ConnectionIO]
    }

  }

  // create annotation_labels from annotations table
  private def createLabels(
      projectId: UUID,
      annotationProjectId: UUID,
      classIds: List[UUID]
  ): ConnectionIO[Either[FailureStage, Unit]] = ???

  // nuke annotate from extras
  private def nukeStaleData(
      project: Project
  ): ConnectionIO[Either[FailureStage, Unit]] = ???

  // do all the stuff
  def liberateProject(
      project: Project
  ): ConnectionIO[Either[FailureStage, Unit]] = {
    val extras = project.extras getOrElse { ().asJson }
    (for {
      annotationProjectId <- EitherT {
        createAnnotationProject(project, extras)
      }
      _ <- EitherT { createProjectTiles(project, annotationProjectId) }
      labelGroupIds <- EitherT {
        createLabelGroups(extras, annotationProjectId)
      }
      classIds <- EitherT {
        createLabelClasses(extras, labelGroupIds.toSet)
      }
      _ <- EitherT { createLabels(project.id, annotationProjectId, classIds) }
      _ <- EitherT { nukeStaleData(project) }
    } yield ()).value
  }
}

object ProjectLiberation extends Job {

  val name: String = "liberate-annotation-projects"

  def runJob(args: List[String]): IO[Unit] = ???

}
