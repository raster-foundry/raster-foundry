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

import com.typesafe.scalalogging.LazyLogging

object TemplateWithRelatedDao extends Dao[Template.WithRelated] with LazyLogging {
  val tableName = "templates"

  val selectF = TemplateDao.selectF

  def getById(templateId: UUID, user: User): ConnectionIO[Option[Template.WithRelated]] = {
    TemplateDao.getById(templateId, user) flatMap {
      case Some(template) =>
        templateToTemplateWithRelated(template, user).map(Some(_))
      case None =>
        Option.empty[Template.WithRelated].pure[ConnectionIO]
    }
  }

  def templateToTemplateWithRelated(template: Template, user: User): ConnectionIO[Template.WithRelated] = {
    (
      TemplateTagDao.getTemplateTags(template),
      TemplateCategoryDao.getTemplateCategories(template),
      TemplateVersionDao.getLatestVersion(template, user)
    ).tupled map {
      case (tags, categories, latestVersion) =>
        template.withRelatedFromComponents(tags, categories, latestVersion)
    }
  }

  def listTemplates(
    pageRequest: PageRequest, templateParams: CombinedTemplateQueryParameters, user: User
  ): ConnectionIO[PaginatedResponse[Template.WithRelated]] = {
    val pageFragment: Fragment = Page(pageRequest)
    val queryFilters: List[Option[Fragment]] = makeFilters(List(templateParams)).flatten
    val templatesIO: ConnectionIO[List[Template]] =
      (selectF ++ Fragments.whereAndOpt(queryFilters: _*) ++ pageFragment)
        .query[Template]
        .stream
        .compile
        .toList

    val withRelatedsIO: ConnectionIO[List[Template.WithRelated]] =
      templatesIO flatMap {
        templates =>
        templates.toNel match  {
          case Some(templates) =>
            templatesToTemplatesWithRelated(templates, user)
          case None => List.empty[Template.WithRelated].pure[ConnectionIO]
        }
      }

    val countIO: ConnectionIO[Int] = (
      fr"SELECT count(*) FROM templates" ++
        Fragments.whereAndOpt(queryFilters: _*)
    ).query[Int].unique

    for {
      page <- withRelatedsIO
      count <- countIO
    } yield {
      val hasPrevious = pageRequest.offset > 0
      val hasNext = ((pageRequest.offset + 1) * pageRequest.limit) < count
      PaginatedResponse[Template.WithRelated](count, hasPrevious, hasNext, pageRequest.offset, pageRequest.limit, page)
    }
  }

  def templatesToTemplatesWithRelated(
    templates: NonEmptyList[Template], user: User
  ): ConnectionIO[List[Template.WithRelated]] = {
    val componentsIO: ConnectionIO[
      (List[(UUID, Tag)], List[(UUID, Category)], List[(UUID, TemplateVersion.WithRelated)])
    ] = {
      val templateIds = templates.map(_.id)
      val tags = getTemplatesTags(templateIds)
      val categories = getTemplatesCategories(templateIds)
      val latestVersions = getTemplatesLatestVersions(templateIds, user)

      (tags, categories, latestVersions).tupled
    }

    componentsIO map {
      case (tags, categories, latestVersions) => {
        val groupedTags = tags.groupBy(_._1)
        val groupedCats = categories.groupBy(_._1)
        val groupedAnalyses = latestVersions.groupBy(_._1)
        templates map { template: Template =>
          template.withRelatedFromComponents(
            groupedTags.getOrElse(template.id, List.empty[(UUID, Tag)]).map(_._2),
            groupedCats.getOrElse(template.id, List.empty[(UUID, Category)]).map(_._2),
            groupedAnalyses.getOrElse(
              template.id, List.empty[(UUID, TemplateVersion.WithRelated)]
            ).map(_._2).headOption
          )
        } toList
      }
    }
  }

  def getTemplatesTags(templateIds: NonEmptyList[UUID]): ConnectionIO[List[(UUID, Tag)]] = {
    (
      sql"""
      SELECT
        tt.template_id,
        t.id, t.created_at, t.modified_at, t.created_by, t.modified_by, t.organization_id, t.tag, t.owner
      FROM template_tags tt
      JOIN tags t ON tt.tag_id = t.id
      WHERE
      """ ++ Fragments.in(fr"tt.template_id", templateIds)
    ).query[(UUID, Tag)].list
  }

  def getTemplatesCategories(templateIds: NonEmptyList[UUID]): ConnectionIO[List[(UUID, Category)]] = {
    (
      sql"""
      SELECT
      tc.template_id,
      c.created_at, c.modified_at, c.created_by, c.modified_by, c.category, c.slug_label
      FROM template_categories tc
      JOIN categories c ON tc.category_slug = c.slug_label
      WHERE
      """ ++ Fragments.in(fr"tc.template_id", templateIds)
    ).query[(UUID, Category)].list
  }

  def getTemplatesLatestVersions(
    templateIds: NonEmptyList[UUID], user: User
  ): ConnectionIO[List[(UUID, TemplateVersion.WithRelated)]] = {
    (
      sql"""
      SELECT
      tv.template_id,
      tv.id, tv.created_at, tv.modified_at, tv.created_by, tv.modified_by, tv.version,
      tv.description, tv.changelog, tv.template_id, tv.analysis_id,
      a.id, a.created_at, a.created_by, a.modified_at, a.modified_by,
      a.visibility, a.organization_id, a.execution_parameters,a.owner, a.name, a.readonly
      FROM template_versions tv
      JOIN analyses a on tv.analysis_id = a.id
      WHERE
      """ ++ Fragments.in(fr"tv.template_id", templateIds)
    ).query[(UUID, TemplateVersion, Analysis)].list.map(results =>
      results.map(tup =>
        (tup._1, tup._2.withRelatedFromComponents(tup._3))
      )
    )
  }

  def makeFilters[T](myList: List[T])(implicit filterable: Filterable[Template.WithRelated, T]) = {
    myList.map(filterable.toFilters(_))
  }
}
