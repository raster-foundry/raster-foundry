package com.rasterfoundry.datamodel

import cats.{Eq, Monoid}
import cats.implicits._
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json}
import io.circe.parser._

import scala.util.{Failure, Success}

case class Action(domain: String, action: String, limit: Option[Long]) {
  def repr: String =
    s"$domain:$action" ++ {
      limit map { lim =>
        s":$lim"
      } getOrElse ""
    }
}

object Action {
  implicit val decAction: Decoder[Action] = Decoder.decodeString.emapTry {
    (s: String) =>
      s.split(":").toList match {
        case domain :: action :: Nil =>
          Success(Action(domain, action, None))
        case domain :: action :: lim :: Nil =>
          Success(Action(domain, action, Some(lim.toLong)))
        case result =>
          Failure(
            DecodingFailure(
              s"Cannot build an action from split string: $result",
              List()
            )
          )
      }
  }
}

sealed abstract class Scope {
  val actions: Set[Action]
}

sealed class SimpleScope(
    val actions: Set[Action]
) extends Scope {
  override def toString: String = actions map { _.repr } mkString (";")
}

object SimpleScope {
  def unapply(simpleScope: SimpleScope): Option[Set[Action]] =
    Some(simpleScope.actions)

  def fromEithers(
      actions: List[Either[DecodingFailure, Action]]
  ): Either[DecodingFailure, SimpleScope] = {
    val failures = actions.filter(_.isLeft)
    if (!failures.isEmpty) {
      Left(DecodingFailure(failures.mkString(";"), List()))
    } else {
      actions.sequence map { acts =>
        new SimpleScope(acts.toSet)
      }
    }
  }
}

sealed class ComplexScope(scopes: Set[Scope]) extends Scope {
  val actions = scopes flatMap { _.actions }
}

object ComplexScope {
  private[datamodel] def apply(scopes: Scope*): ComplexScope =
    new ComplexScope(scopes.toSet)

  def unapply(complexScope: ComplexScope): Option[Set[Action]] =
    Some(complexScope.actions)
}

object Scope {

  implicit val eqScope = new Eq[Scope] {
    def eqv(x: Scope, y: Scope): Boolean =
      x.actions == y.actions
  }

  implicit val monoidScope = new Monoid[Scope] {
    def empty = Scopes.NoAccess
    def combine(x: Scope, y: Scope): Scope =
      ComplexScope(x, y)
  }

  implicit val encScope: Encoder[Scope] = new Encoder[Scope] {
    def apply(thing: Scope): Json = thing match {
      case Scopes.Uploader            => Json.fromString("uploader")
      case Scopes.AnalysesCRUD        => Json.fromString("analyses:crud")
      case Scopes.AnalysesMultiPlayer => Json.fromString("analyses:multiplayer")
      case Scopes.DatasourcesCRUD     => Json.fromString("datasources:crud")
      case Scopes.OrganizationsUserAdmin =>
        Json.fromString("organizations:userAdmin")
      case Scopes.ProjectExport      => Json.fromString("projects:exportFullAccess")
      case Scopes.ProjectsFullAccess => Json.fromString("projects:fullAccess")
      case Scopes.RasterFoundryOrganizationAdmin =>
        Json.fromString("organizations:admin")
      case Scopes.RasterFoundryTeamAdmin => Json.fromString("teams:admin")
      case Scopes.ScenesCRUD             => Json.fromString("scenes:crud")
      case Scopes.ScenesMultiPlayer      => Json.fromString("scenes:multiplayer")
      case Scopes.ShapesFullAccess       => Json.fromString("shapes:fullAccess")
      case Scopes.TeamsEdit              => Json.fromString("teams:edit")
      case Scopes.TemplatesCRUD          => Json.fromString("templates:crud")
      case Scopes.TemplatesMultiPlayer =>
        Json.fromString("templates:multiplayer")
      case Scopes.UploadsCRUD => Json.fromString("uploads:crud")
      case SimpleScope(actions) =>
        Json.fromString((actions map { action =>
          action.repr
        }).mkString(";"))
      case ComplexScope(actions) =>
        Json.fromString((actions map { action =>
          action.repr
        }).mkString(";"))
    }
  }

  implicit val decScope: Decoder[Scope] = new Decoder[Scope] {
    def apply(c: HCursor): Decoder.Result[Scope] = c.value.asString match {
      case Some("uploader")             => Right(Scopes.Uploader)
      case Some("analyses:crud")        => Right(Scopes.AnalysesCRUD)
      case Some("analyses:multiplayer") => Right(Scopes.AnalysesMultiPlayer)
      case Some("datasources:crud")     => Right(Scopes.DatasourcesCRUD)
      case Some("organizations:userAdmin") =>
        Right(Scopes.OrganizationsUserAdmin)
      case Some("projects:exportFullAccess") => Right(Scopes.ProjectExport)
      case Some("projects:fullAccess")       => Right(Scopes.ProjectsFullAccess)
      case Some("organizations:admin") =>
        Right(Scopes.RasterFoundryOrganizationAdmin)
      case Some("teams:admin")           => Right(Scopes.RasterFoundryTeamAdmin)
      case Some("scenes:crud")           => Right(Scopes.ScenesCRUD)
      case Some("scenes:multiplayer")    => Right(Scopes.ScenesMultiPlayer)
      case Some("shapes:fullAccess")     => Right(Scopes.ShapesFullAccess)
      case Some("teams:edit")            => Right(Scopes.TeamsEdit)
      case Some("templates:crud")        => Right(Scopes.TemplatesCRUD)
      case Some("templates:multiplayer") => Right(Scopes.TemplatesMultiPlayer)
      case Some("uploads:crud")          => Right(Scopes.UploadsCRUD)
      case Some(s) =>
        SimpleScope.fromEithers((s.split(";").toList match {
          case List("") => List.empty
          case actions =>
            actions.map(
              act =>
                decode[Action](s""""$act"""")
                  .leftMap(err => DecodingFailure(err.getMessage, Nil))
            )
        }))
      case _ => Left(DecodingFailure("Scope", c.history))
    }
  }
}

object Scopes {

  private def makeCRUDActions(domain: String): Set[Action] =
    Set("read", "create", "update", "delete") map { s =>
      makeAction(domain, s)
    }

  private def makeAction(
      domain: String,
      action: String,
      limit: Option[Long] = None
  ): Action =
    Action(domain, action, limit)

  case object NoAccess extends SimpleScope(Set.empty)

  case object Uploader
      extends SimpleScope(
        Set("read", "create", "delete") map { makeAction("uploads", _) }
      )

  case object UploadsCRUD
      extends ComplexScope(
        Set(
          Uploader,
          new SimpleScope(Set(makeAction("uploads", "delete")))
        )
      )

  case object ScenesCRUD extends SimpleScope(makeCRUDActions("scenes"))

  case object ScenesMultiPlayer
      extends SimpleScope(Set(makeAction("scenes", "share")))

  case object ScenesFullAccess
      extends ComplexScope(Set(ScenesCRUD, ScenesMultiPlayer))

  case object ProjectsCRUD extends SimpleScope(makeCRUDActions("projects"))

  case object ProjectExport
      extends SimpleScope(Set("listExports", "createExport") map {
        makeAction("projects", _)
      })

  case object ProjectAnnotate
      extends SimpleScope(
        Set(
          "createAnnotation",
          "deleteAnnotation",
          "editAnnotation"
        ) map {
          makeAction("projects", _)
        }
      )

  case object DatasourcesCRUD
      extends SimpleScope(makeCRUDActions("datasources"))

  case object ProjectsMultiPlayer
      extends SimpleScope(Set(makeAction("projects", "share")))

  case object ProjectsFullAccess
      extends ComplexScope(
        Set(ProjectsCRUD, ProjectsMultiPlayer, ProjectExport, ProjectAnnotate)
      )

  case object ShapesCRUD extends SimpleScope(makeCRUDActions("shapes"))

  case object ShapesMultiPlayer
      extends SimpleScope(Set(makeAction("shapes", "share")))

  case object ShapesFullAccess
      extends ComplexScope(Set(ShapesCRUD, ShapesMultiPlayer))

  case object TemplatesCRUD extends SimpleScope(makeCRUDActions("templates"))
  case object TemplatesMultiPlayer
      extends SimpleScope(Set(makeAction("templates", "share")))
  case object TemplatesFullAccess
      extends ComplexScope(Set(TemplatesCRUD, TemplatesMultiPlayer))

  case object AnalysesCRUD extends SimpleScope(makeCRUDActions("analyses"))
  case object AnalysesMultiPlayer
      extends SimpleScope(Set(makeAction("analyses", "share")))
  case object AnalysesFullAccess
      extends ComplexScope(Set(AnalysesCRUD, AnalysesMultiPlayer))

  case object RasterFoundryUser
      extends ComplexScope(
        Set(
          AnalysesFullAccess,
          DatasourcesCRUD,
          ProjectsFullAccess,
          ScenesFullAccess,
          ShapesFullAccess,
          TemplatesFullAccess,
          Uploader,
          // Regular users can view their teams and organizations, but can do nothing
          // else in that domain
          new SimpleScope(Set("teams", "organizations") flatMap { domain =>
            Set(makeAction(domain, "read"), makeAction(domain, "readUsers"))
          })
        )
      )

  case object TeamsUserAdmin
      extends SimpleScope(
        Set("addUser", "removeUser", "editUserRole") map {
          makeAction("teams", _)
        }
      )

  case object OrganizationsUserAdmin
      extends SimpleScope(
        Set("addUser", "removeUser", "editUserRole") map {
          makeAction("organizations", _)
        }
      )

  case object TeamsEdit
      extends SimpleScope(
        Set("edit", "delete") map {
          makeAction("teams", _)
        }
      )

  case object RasterFoundryTeamAdmin
      extends ComplexScope(
        Set(
          RasterFoundryUser,
          TeamsUserAdmin,
          TeamsEdit
        )
      )

  case object RasterFoundryOrganizationAdmin
      extends ComplexScope(
        Set(
          RasterFoundryTeamAdmin,
          OrganizationsUserAdmin,
          new SimpleScope(Set(makeAction("teams", "create")))
        )
      )

  case object RasterFoundryPlatformAdmin
      extends ComplexScope(
        Set(
          RasterFoundryOrganizationAdmin,
          new SimpleScope(Set(makeAction("organizations", "create")))
        )
      )
}
