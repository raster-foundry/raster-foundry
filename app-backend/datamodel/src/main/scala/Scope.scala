package com.rasterfoundry.datamodel

import cats.{Eq, Monoid}
import cats.implicits._
import _root_.io.circe.{Decoder, Encoder, Json}
import scala.util.{Failure, Success, Try}

case class Action(domain: String, action: String, limit: Option[Long]) {
  def repr: String =
    s"$domain:$action" ++ {
      limit map { lim =>
        s":$lim"
      } getOrElse ""
    }
}

object Action {
  private[datamodel] def fromString(s: String): Try[Action] =
    s.split(":").toList match {
      case domain :: action :: Nil =>
        Success(Action(domain, action, None))
      case domain :: action :: lim :: Nil =>
        Success(Action(domain, action, Some(lim.toLong)))
      case result =>
        Failure(
          new Exception(s"Cannot build an action from split string: $result")
        )
    }
}

sealed abstract class Scope {
  val actions: Set[Action]
}

sealed class SimpleScope(
    val actions: Set[Action]
) extends Scope

object SimpleScope {
  def unapply(simpleScope: SimpleScope): Option[Set[Action]] =
    Some(simpleScope.actions)
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

  private[datamodel] def fromString(s: String): Try[Scope] = s match {
    case "uploader"                  => Success(Scopes.Uploader)
    case "analyses:crud"             => Success(Scopes.AnalysesCRUD)
    case "analyses:multiplayer"      => Success(Scopes.AnalysesMultiPlayer)
    case "datasources:crud"          => Success(Scopes.DatasourcesCRUD)
    case "organizations:userAdmin"   => Success(Scopes.OrganizationsUserAdmin)
    case "projects:exportFullAccess" => Success(Scopes.ProjectExport)
    case "projects:fullAccess"       => Success(Scopes.ProjectsFullAccess)
    case "organizations:admin"       => Success(Scopes.RasterFoundryOrganizationAdmin)
    case "teams:admin"               => Success(Scopes.RasterFoundryTeamAdmin)
    case "scenes:crud"               => Success(Scopes.ScenesCRUD)
    case "scenes:multiplayer"        => Success(Scopes.ScenesMultiPlayer)
    case "shapes:fullAccess"         => Success(Scopes.ShapesFullAccess)
    case "teams:edit"                => Success(Scopes.TeamsEdit)
    case "templates:crud"            => Success(Scopes.TemplatesCRUD)
    case "templates:multiplayer"     => Success(Scopes.TemplatesMultiPlayer)
    case "uploads:crud"              => Success(Scopes.UploadsCRUD)
    case s =>
      (s.split(";").toList match {
        case List("") => Success(List.empty)
        case actions =>
          actions traverse { scopePart =>
            Action.fromString(scopePart)
          }
      }).map { scopeParts =>
        new SimpleScope(scopeParts.toSet)
      }
  }

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

  implicit val decScope: Decoder[Scope] = Decoder.decodeString.emapTry {
    (s: String) =>
      Scope.fromString(s)
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
          new SimpleScope(Set("team", "organizations") map {
            makeAction(_, "read")
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
