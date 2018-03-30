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

object TemplateTagDao extends Dao[TemplateTag] {
  val tableName = "template_tags"

  val selectF = sql"""
    SELECT template_id, tag_id
    FROM
    """ ++ tableF

  def insert(template: Template, tag: Tag): ConnectionIO[TemplateTag] = {
    sql"""
    INSERT INTO template_tags (template_id, tag_id)
    VALUES (${template.id}, ${tag.id})
    """.update.withUniqueGeneratedKeys[TemplateTag]("template_id", "tag_id")
  }

  def delete(template: Template, tag: Tag): ConnectionIO[Int] = {
    query.filter(fr"template_id = ${template.id}")
      .filter(fr"tag-id = ${tag.id}")
      .delete
  }

  def createMany(template: Template, tagList: NonEmptyList[UUID]): ConnectionIO[Int] = {
    val insertQuery = "INSERT INTO template_tags (template_id, tag_id) VALUES (?, ?)"
    Update[(UUID, UUID)](insertQuery).updateMany(tagList.map(tagId => (template.id, tagId)))
  }

  def setTemplateTags(template: Template, tagList: List[UUID]): ConnectionIO[Int] = {
    tagList.toNel match {
      case Some(tags: NonEmptyList[UUID]) =>
        createMany(template, tags).flatMap(_ =>
          query.filter(fr"template_id = ${template.id}")
            .filter(Fragments.in(fr"tag_id", tags))
            .delete
        )

      case None =>
        query.filter(fr"template_id = ${template.id}")
          .delete
    }
  }

  def getTemplateTags(template: Template): ConnectionIO[List[Tag]] = {
    sql"""
    SELECT tags.*
    FROM tags JOIN template_tags on tags.id = template_tags.tag_id
    WHERE template_tags.template_id = ${template.id}
    """.query[Tag].list
  }


}
