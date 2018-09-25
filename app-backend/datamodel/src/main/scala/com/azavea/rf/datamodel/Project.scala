package com.azavea.rf.datamodel

import java.sql.Timestamp
import java.util.UUID

import cats.implicits._
import cats.syntax.either._
import com.azavea.rf.bridge._
import geotrellis.vector.io.json.GeoJsonSupport
import geotrellis.vector.{Geometry, Projected}
import io.circe._
import io.circe.generic.JsonCodec
import io.circe.generic.semiauto._
import io.circe.syntax._

@JsonCodec
final case class Project(id: UUID,
                         createdAt: Timestamp,
                         modifiedAt: Timestamp,
                         createdBy: String,
                         modifiedBy: String,
                         owner: String,
                         name: String,
                         slugLabel: String,
                         description: String,
                         visibility: Visibility,
                         tileVisibility: Visibility,
                         isAOIProject: Boolean,
                         aoiCadenceMillis: Long, /* Milliseconds */
                         aoisLastChecked: Timestamp,
                         tags: List[String] = List.empty,
                         extent: Option[Projected[Geometry]] = None,
                         manualOrder: Boolean = true,
                         isSingleBand: Boolean = false,
                         singleBandOptions: Option[SingleBandOptions.Params],
                         defaultAnnotationGroup: Option[UUID],
                         extras: Option[Json])

/** Case class for project creation */
object Project extends GeoJsonSupport {

  implicit val thinUserEncoder: Encoder[User] =
    Encoder.forProduct3("id", "name", "profileImageUri")(
      user => (user.id, user.name, user.profileImageUri)
    )
  implicit val thinUserDecoder: Decoder[User.Create] =
    Decoder.forProduct3("id", "name", "profileImageUri")(
      (id: String, name: String, profileImageUri: String) =>
        User.Create(id, Viewer, "", name, profileImageUri)
    )

  /* One week, in milliseconds */
  val DEFAULT_CADENCE: Long = 604800000

  def tupled = (Project.apply _).tupled

  def create = Create.apply _

  def slugify(input: String): String = {
    import java.text.Normalizer
    Normalizer
      .normalize(input, Normalizer.Form.NFD)
      .replaceAll(
        "[^\\w\\s-]",
        "".replace('-', ' ')
          .trim
          .replaceAll("\\s+", "-")
          .toLowerCase
      )
  }

  final case class Create(name: String,
                          description: String,
                          visibility: Visibility,
                          tileVisibility: Visibility,
                          isAOIProject: Boolean,
                          aoiCadenceMillis: Long,
                          owner: Option[String],
                          tags: List[String],
                          isSingleBand: Boolean,
                          singleBandOptions: Option[SingleBandOptions.Params],
                          extras: Option[Json] = Some("{}".asJson))
      extends OwnerCheck {
    def toProject(user: User): Project = {
      val now = new Timestamp(new java.util.Date().getTime)

      val ownerId = checkOwner(user, this.owner)

      Project(
        UUID.randomUUID, // primary key
        now, // createdAt
        now, // modifiedAt
        user.id, // createdBy
        user.id, // modifiedBy
        ownerId, // owner
        name,
        slugify(name),
        description,
        visibility,
        tileVisibility,
        isAOIProject,
        aoiCadenceMillis,
        new Timestamp(now.getTime - aoiCadenceMillis),
        tags,
        None,
        manualOrder = true,
        isSingleBand = isSingleBand,
        singleBandOptions,
        None,
        extras
      )
    }
  }

  object Create {

    /** Custon Circe decoder for [[Create]], to handle default values. */
    val decWithUserString: Decoder[Create] = Decoder.instance(
      c =>
        (
          c.downField("name").as[String],
          c.downField("description").as[String],
          c.downField("visibility").as[Visibility],
          c.downField("tileVisibility").as[Visibility],
          c.downField("isAOIProject")
            .as[Option[Boolean]]
            .map(_.getOrElse(false)),
          c.downField("aoiCadenceMillis")
            .as[Option[Long]]
            .map(_.getOrElse(DEFAULT_CADENCE)),
          c.downField("owner").as[Option[String]],
          c.downField("tags").as[List[String]],
          c.downField("isSingleBand")
            .as[Option[Boolean]]
            .map(_.getOrElse(false)),
          c.downField("singleBandOptions").as[Option[SingleBandOptions.Params]],
          c.downField("extras").as[Option[Json]]
        ).mapN(Create.apply)
    )

    val decWithUserObject: Decoder[Create] = Decoder.instance(
      c =>
        (
          c.downField("name").as[String],
          c.downField("description").as[String],
          c.downField("visibility").as[Visibility],
          c.downField("tileVisibility").as[Visibility],
          c.downField("isAOIProject")
            .as[Option[Boolean]]
            .map(_.getOrElse(false)),
          c.downField("aoiCadenceMillis")
            .as[Option[Long]]
            .map(_.getOrElse(DEFAULT_CADENCE)),
          c.downField("owner").as[User.Create] map { usr: User.Create =>
            Some(usr.id)
          },
          c.downField("tags").as[List[String]],
          c.downField("isSingleBand")
            .as[Option[Boolean]]
            .map(_.getOrElse(false)),
          c.downField("singleBandOptions").as[Option[SingleBandOptions.Params]],
          c.downField("extras").as[Option[Json]]
        ).mapN(Create.apply)
    )

    implicit val dec: Decoder[Create] = decWithUserString or decWithUserObject

    implicit val enc: Encoder[Create] = deriveEncoder
  }

  final case class WithUser(id: UUID,
                            createdAt: Timestamp,
                            modifiedAt: Timestamp,
                            createdBy: String,
                            modifiedBy: String,
                            owner: User,
                            name: String,
                            slugLabel: String,
                            description: String,
                            visibility: Visibility,
                            tileVisibility: Visibility,
                            isAOIProject: Boolean,
                            aoiCadenceMillis: Long, /* Milliseconds */
                            aoisLastChecked: Timestamp,
                            tags: List[String],
                            extent: Option[Projected[Geometry]],
                            manualOrder: Boolean,
                            isSingleBand: Boolean,
                            singleBandOptions: Option[SingleBandOptions.Params],
                            extras: Option[Json] = Some("{}".asJson))

  object WithUser {
    def apply(project: Project, user: User): WithUser = {
      WithUser(
        project.id,
        project.createdAt,
        project.modifiedAt,
        project.createdBy,
        project.modifiedBy,
        user,
        project.name,
        project.slugLabel,
        project.description,
        project.visibility,
        project.tileVisibility,
        project.isAOIProject,
        project.aoiCadenceMillis,
        project.aoisLastChecked,
        project.tags,
        project.extent,
        project.manualOrder,
        project.isSingleBand,
        project.singleBandOptions,
        project.extras
      )
    }

    // This has an encoder and no decoder because a Project.WithUser should never be POSTed
    implicit val withUserEncoder: ObjectEncoder[WithUser] =
      deriveEncoder[WithUser]
  }
}
