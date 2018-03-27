package com.azavea.rf.database

import java.sql.Timestamp

import com.azavea.rf.database.Implicits._
import com.azavea.rf.datamodel._
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import cats._
import cats.data._
import cats.effect.IO
import cats.implicits._
import java.util.UUID

import com.azavea.rf.database.util.Page
import com.lonelyplanet.akka.http.extensions.PageRequest

object WorkspaceWithRelatedDao extends Dao[Workspace.WithRelated] {
  val tableName = "workspaces"

  val selectF = WorkspaceDao.selectF

  def listWorkspaces(
    pageRequest: PageRequest, workspaceParams: CombinedWorkspaceQueryParameters, user: User
  ): ConnectionIO[PaginatedResponse[Workspace.WithRelated]] = {

    val pageFragment: Fragment = Page(pageRequest)
    val queryFilters: List[Option[Fragment]] = makeFilters(List(workspaceParams)).flatten
    val workspacesIO: ConnectionIO[List[Workspace]] =
      (selectF ++ Fragments.whereAndOpt(queryFilters: _*) ++ pageFragment)
        .query[Workspace]
        .stream
        .compile
        .toList

    val withRelatedsIO: ConnectionIO[List[Workspace.WithRelated]] =
      workspacesIO flatMap {
        workspaces =>
        workspaces.toNel match  {
          case Some(workspaces) =>
            workspacesToWorkspacesWithRelated(workspaces, user)
          case None => List.empty[Workspace.WithRelated].pure[ConnectionIO]
        }
      }

    val countIO: ConnectionIO[Int] = (
      fr"SELECT count(*) FROM workspaces" ++
        Fragments.whereAndOpt(queryFilters: _*)
    ).query[Int].unique

    for {
      page <- withRelatedsIO
      count <- countIO
    } yield {
      val hasPrevious = pageRequest.offset > 0
      val hasNext = ((pageRequest.offset + 1) * pageRequest.limit) < count
      PaginatedResponse[Workspace.WithRelated](count, hasPrevious, hasNext, pageRequest.offset, pageRequest.limit, page)
    }
  }

  def workspacesToWorkspacesWithRelated(workspaces: NonEmptyList[Workspace], user: User):
      ConnectionIO[List[Workspace.WithRelated]] = {
    val componentsIO: ConnectionIO[(List[(UUID, Tag)], List[(UUID, Category)], List[(UUID, Analysis)])] = {
      val workspaceIds = workspaces.map(_.id)
      val tags = getWorkspacesTags(workspaceIds)
      val categories = getWorkspacesCategories(workspaceIds)
      val analyses = getWorkspacesAnalyses(workspaceIds, user)
      (tags, categories, analyses).tupled
    }

    componentsIO map {
      case (tags, categories, analyses) => {
        val groupedTags = tags.groupBy(_._1)
        val groupedCats = categories.groupBy(_._1)
        val groupedAnalyses = analyses.groupBy(_._1)
        workspaces map { workspace: Workspace =>
          workspace.withRelatedFromComponents(
            groupedTags.getOrElse(workspace.id, List.empty[(UUID, Tag)]).map(_._2),
            groupedCats.getOrElse(workspace.id, List.empty[(UUID, Category)]).map(_._2),
            groupedAnalyses.getOrElse(workspace.id, List.empty[(UUID, Analysis)]).map(_._2)
          )
        } toList
      }
    }
  }

  def getWorkspacesTags(workspaceIds: NonEmptyList[UUID]): ConnectionIO[List[(UUID, Tag)]] = {
    (
      sql"""
      SELECT wt.id, tags.*
      FROM workspace_tags wt
      JOIN tags ON wt.tag_id = tags.id
      """ ++ Fragments.in(fr"wt.workspace_id", workspaceIds)
    ).query[(UUID, Tag)].list
  }

  def getWorkspacesCategories(workspaceIds: NonEmptyList[UUID]):
      ConnectionIO[List[(UUID, Category)]] = {
      (
        sql"""
      SELECT wc.id, categories.*
      FROM workspace_categories wc
      JOIN categories ON wc.category_slug = categories.slug_label
      """ ++ Fragments.in(fr"wc.workspace_id", workspaceIds)
      ).query[(UUID, Category)].list
  }

  def getWorkspacesAnalyses(workspaceIds: NonEmptyList[UUID], user: User):
      ConnectionIO[List[(UUID, Analysis)]] = {
      (
        sql"""
      SELECT wa.id, tags.*
      FROM workspace_analyses wa
      JOIN tags ON wa.analysis_id = tags.id
      """ ++ Fragments.in(fr"wa.workspace_id", workspaceIds)
      ).query[(UUID, Analysis)].list
  }

  def getById(workspaceId: UUID, user: User): ConnectionIO[Option[Workspace.WithRelated]] = {
    WorkspaceDao.getById(workspaceId, user) flatMap {
      case Some(workspace) =>
        workspaceToWorkspaceWithRelated(workspace, user).map(Some(_))
      case None =>
        Option.empty[Workspace.WithRelated].pure[ConnectionIO]
    }
  }

  def workspaceToWorkspaceWithRelated(workspace: Workspace, user: User): ConnectionIO[Workspace.WithRelated] = {
    (
      WorkspaceTagDao.getWorkspaceTags(workspace),
      WorkspaceCategoryDao.getWorkspaceCategories(workspace),
      WorkspaceAnalysisDao.getWorkspaceAnalyses(workspace, user)
    ).tupled map {
      case (tags, categories, analyses) =>
        workspace.withRelatedFromComponents(tags, categories, analyses)
    }
  }

  def makeFilters[T](myList: List[T])(implicit filterable: Filterable[Workspace.WithRelated, T]) = {
    myList.map(filterable.toFilters(_))
  }
}
