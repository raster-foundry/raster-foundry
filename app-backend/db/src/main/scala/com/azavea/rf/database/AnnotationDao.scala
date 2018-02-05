package com.azavea.rf.database

import com.azavea.rf.database.meta.RFMeta._
import com.azavea.rf.database.util._
import com.azavea.rf.datamodel._

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import cats._, cats.data._, cats.effect.IO, cats.implicits._
import geotrellis.slick.Projected
import geotrellis.vector.Geometry
import com.lonelyplanet.akka.http.extensions.PageRequest

import scala.concurrent.Future
import java.sql.Timestamp
import java.util.{Date, UUID}


object AnnotationDao extends Dao[Annotation] {

  val tableName = "annotations"

  val selectF =
    fr"""
      SELECT
        id, project_id, created_at, created_by, modified_at, modified_by, owner,
        organization_id, label, description, machine_generated, confidence,
        quality, geometry
      FROM
    """ ++ tableF

  def select(id: UUID) =
    (selectF ++ fr"WHERE id = $id").query[Annotation].unique

  def listFilters(params: AnnotationQueryParameters, user: User, projectId: Option[UUID]) =
    Filters.organization(params.orgParams) ++
    Filters.user(params.userParams) ++ List(
      params.label.map({ label => fr"label = $label" }),
      params.machineGenerated.map({ mg => fr"machine_generated = $mg" }),
      params.minConfidence.map({ minc => fr"min_confidence = $minc" }),
      params.maxConfidence.map({ maxc => fr"max_confidence = $maxc" }),
      params.quality.map({ quality => fr"quality = $quality" }),
      projectId.map({ pid => fr"project_id = $pid" })
    )

  def create(
    projectId: UUID,
    user: User,
    owner: Option[String],
    organizationId: UUID,
    label: String,
    description: Option[String],
    machineGenerated: Option[Boolean],
    confidence: Option[Double],
    quality: Option[AnnotationQuality],
    geometry: Option[Projected[Geometry]]
  ): ConnectionIO[Annotation] = {
    val now = new Timestamp((new java.util.Date()).getTime())
    val ownerId = util.Ownership.checkOwner(user, owner)
    (fr"INSERT INTO" ++ tableF ++ fr"""
        (id, project_id, created_at, created_by, modified_at, modified_by, owner,
        organization_id, label, description, machine_generated, confidence,
        quality, geometry)
      VALUES
        (${UUID.randomUUID}, $projectId, $now, ${user.id}, $now, ${user.id}, $ownerId,
        $organizationId, $label, $description, $machineGenerated, $confidence,
        $quality, $geometry)
    """).update.withUniqueGeneratedKeys[Annotation](
      "id", "project_id", "created_at", "created_by", "modified_at", "modified_by", "owner",
      "organization_id", "label", "description", "machine_generated", "confidence",
      "quality", "geometry"
    )
  }
}

object AnnotationJson {
  import io.circe._
  import scala.concurrent.Future
  // Potential strategy for replacement of `Annotation.Create`
  def create(
    annotationJson: Json,
    projectId: UUID,
    user: User
  )(implicit xa: Transactor[IO]): Either[DecodingFailure, Future[Annotation]] = {
    val c = annotationJson.hcursor
    (c.get[Option[String]]("owner"),
     c.get[String]("label"),
     c.get[Option[String]]("description"),
     c.get[Option[Boolean]]("machineGenerated"),
     c.get[Option[Double]]("confidence"),
     c.get[Option[AnnotationQuality]]("quality"),
     c.get[Option[Projected[Geometry]]]("geometry"))
       .mapN({ case (owner, label, description, machineGenerated, confidence, quality, geometry) =>
         val creation = AnnotationDao.create(
           projectId, user, owner, user.organizationId, label, description,
           machineGenerated, confidence, quality, geometry
         )
         creation.transact(xa).unsafeToFuture()
       })
  }
}

