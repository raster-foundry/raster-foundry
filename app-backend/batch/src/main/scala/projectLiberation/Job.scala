package com.rasterfoundry.batch.projectLiberation

import com.rasterfoundry.batch.Job
import com.rasterfoundry.datamodel.Project
import com.rasterfoundry.database.ProjectDao

import cats.effect.IO
import doobie.ConnectionIO

import java.util.UUID

object ProjectLiberation extends Job {
  private def getAnnotationProjects: ConnectionIO[List[Project]] = ???

  // make an annotation project from the existing project
  private def createAnnotationProject(project: Project): ConnectionIO[UUID] =
    ???

  // create tiles entries for tms layer
  private def createProjectTiles(
      project: Project,
      annotationProjectId: UUID
  ): ConnectionIO[Unit] = ???

  // maybe (UUID, name)?
  private def createLabelClasses(
      annotationProjectId: UUID
  ): ConnectionIO[List[(UUID, String)]] = ???

  // create annotation_labels from annotations table
  private def createLabels(
      projectId: UUID,
      annotationProjectId: UUID,
      classIds: List[(UUID, String)]
  ): ConnectionIO[Unit] = ???

  // nuke annotate from extras
  private def nukeStaleData(project: Project): ConnectionIO[Unit] = ???

  // do all the stuff
  def freeAMind(project: Project): ConnectionIO[Unit] =
    for {
      annotationProjectId <- createAnnotationProject(project)
      _ <- createProjectTiles(project, annotationProjectId)
      classIds <- createLabelClasses(annotationProjectId)
      _ <- createLabels(project.id, annotationProjectId, classIds)
      _ <- nukeStaleData(project)
    } yield ()

  def runJob(args: List[String]): IO[Unit] = ???
}
