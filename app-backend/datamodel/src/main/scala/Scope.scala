package com.rasterfoundry.datamodel

import cats.{Eq, Monoid}
import cats.implicits._
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json}
import io.circe.parser._

import scala.util.{Failure, Success, Try}

sealed abstract class Domain(repr: String) {
  override def toString: String = repr
}
object Domain {
  case object Uploads extends Domain("uploads")
  case object Scenes extends Domain("scenes")
  case object Projects extends Domain("projects")
  case object Datasources extends Domain("datasources")
  case object Shapes extends Domain("shapes")
  case object Templates extends Domain("templates")
  case object Analyses extends Domain("analyses")
  case object Teams extends Domain("teams")
  case object Organizations extends Domain("organizations")

  def fromStringTry(s: String): Try[Domain] = s match {
    case "uploads"       => Success(Uploads)
    case "scenes"        => Success(Scenes)
    case "projects"      => Success(Projects)
    case "datasources"   => Success(Datasources)
    case "shapes"        => Success(Shapes)
    case "templates"     => Success(Templates)
    case "analyses"      => Success(Analyses)
    case "teams"         => Success(Teams)
    case "organizations" => Success(Organizations)
    case _               => Failure(new Exception(s"Cannot parse Domain from string $s"))
  }
}

sealed abstract class Action(repr: String) {
  override def toString: String = repr
}

object Action {
  case object Read extends Action("read")
  case object Create extends Action("create")
  case object Delete extends Action("delete")
  case object Update extends Action("update")
  case object Share extends Action("share")
  case object ListExports extends Action("listExports")
  case object CreateExport extends Action("createExport")
  case object CreateAnnotation extends Action("createAnnotation")
  case object DeleteAnnotation extends Action("deleteAnnotation")
  case object UpdateAnnotation extends Action("updateAnnotation")
  case object ReadUsers extends Action("readUsers")
  case object AddUser extends Action("addUser")
  case object RemoveUser extends Action("removeUser")
  case object UpdateUserRole extends Action("updateUserRole")

  def fromStringTry(s: String): Try[Action] = s match {
    case "read"             => Success(Read)
    case "create"           => Success(Create)
    case "delete"           => Success(Delete)
    case "update"           => Success(Update)
    case "share"            => Success(Share)
    case "listExports"      => Success(ListExports)
    case "createExport"     => Success(CreateExport)
    case "createAnnotation" => Success(CreateAnnotation)
    case "deleteAnnotation" => Success(DeleteAnnotation)
    case "updateAnnotation" => Success(UpdateAnnotation)
    case "readUsers"        => Success(ReadUsers)
    case "addUser"          => Success(AddUser)
    case "removeUser"       => Success(RemoveUser)
    case "updateUserRole"   => Success(UpdateUserRole)
    case _                  => Failure(new Exception(s"Cannot parse Action from string $s"))
  }
}

/** A ScopedAction is a combination of a domain, an action, and a limit.
  *
  * Domains are an enum containing all of the area of the application that can
  * have permissions attached to them.
  * Actions are an enum containing all of the things it's possible to do in any
  * domain.
  * While making these enums doesn't prevent the creation of bonkers permissions
  * (e.g. teams:createExport:-4), it at least prevents the creation of permissions
  * that are one typo away from valid (e.g. projects:createExpert).
  */
case class ScopedAction(domain: Domain, action: Action, limit: Option[Long]) {
  def repr: String =
    s"$domain:$action" ++ {
      limit map { lim =>
        s":$lim"
      } getOrElse ""
    }
}

object ScopedAction {
  implicit val decScopedAction: Decoder[ScopedAction] =
    Decoder.decodeString.emapTry { (s: String) =>
      s.split(":").toList match {
        case domain :: action :: Nil =>
          (Domain.fromStringTry(domain), Action.fromStringTry(action)) mapN {
            case (dom, act) =>
              ScopedAction(dom, act, None)
          }
        case domain :: action :: lim :: Nil =>
          (Domain.fromStringTry(domain), Action.fromStringTry(action)) mapN {
            case (dom, act) => ScopedAction(dom, act, Some(lim.toLong))
          }
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
  val actions: Set[ScopedAction]
}

sealed class SimpleScope(
    val actions: Set[ScopedAction]
) extends Scope {
  override def toString: String = actions map { _.repr } mkString (";")
}

object SimpleScope {
  def unapply(simpleScope: SimpleScope): Option[Set[ScopedAction]] =
    Some(simpleScope.actions)

  def fromEithers(
      actions: List[Either[DecodingFailure, ScopedAction]]
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

  def unapply(complexScope: ComplexScope): Option[Set[ScopedAction]] =
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
      case Scopes.RasterFoundryUser =>
        Json.fromString("platformUser")
      case Scopes.RasterFoundryPlatformAdmin => Json.fromString("platforms:admin")
      case Scopes.RasterFoundryTeamAdmin => Json.fromString("teams:admin")
      case Scopes.ScenesCRUD             => Json.fromString("scenes:crud")
      case Scopes.ScenesMultiPlayer      => Json.fromString("scenes:multiplayer")
      case Scopes.ShapesFullAccess       => Json.fromString("shapes:fullAccess")
      case Scopes.TeamsUpdate            => Json.fromString("teams:update")
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
      case Some("platformUser") => Right(Scopes.RasterFoundryUser)
      case Some("platforms:admin") => Right (Scopes.RasterFoundryPlatformAdmin)
      case Some("teams:admin")           => Right(Scopes.RasterFoundryTeamAdmin)
      case Some("scenes:crud")           => Right(Scopes.ScenesCRUD)
      case Some("scenes:multiplayer")    => Right(Scopes.ScenesMultiPlayer)
      case Some("shapes:fullAccess")     => Right(Scopes.ShapesFullAccess)
      case Some("teams:update")          => Right(Scopes.TeamsUpdate)
      case Some("templates:crud")        => Right(Scopes.TemplatesCRUD)
      case Some("templates:multiplayer") => Right(Scopes.TemplatesMultiPlayer)
      case Some("uploads:crud")          => Right(Scopes.UploadsCRUD)
      case Some(s) =>
        SimpleScope.fromEithers((s.split(";").toList match {
          case List("") => List.empty
          case actions =>
            actions.map(
              act =>
                decode[ScopedAction](s""""$act"""")
                  .leftMap(err => DecodingFailure(err.getMessage, Nil))
            )
        }))
      case _ => Left(DecodingFailure("Scope", c.history))
    }
  }
}

object Scopes {

  private def makeCRUDScopedActions(domain: Domain): Set[ScopedAction] =
    Set(Action.Read, Action.Create, Action.Update, Action.Delete) map { s =>
      makeScopedAction(domain, s)
    }

  private def makeScopedAction(
      domain: Domain,
      action: Action,
      limit: Option[Long] = None
  ): ScopedAction =
    ScopedAction(domain, action, limit)

  case object NoAccess extends SimpleScope(Set.empty)

  case object Uploader
      extends SimpleScope(
        Set(Action.Read, Action.Update, Action.Delete) map {
          makeScopedAction(Domain.Uploads, _)
        }
      )

  case object UploadsCRUD
      extends ComplexScope(
        Set(
          Uploader,
          new SimpleScope(Set(makeScopedAction(Domain.Uploads, Action.Delete)))
        )
      )

  case object ScenesCRUD
      extends SimpleScope(makeCRUDScopedActions(Domain.Scenes))

  case object ScenesMultiPlayer
      extends SimpleScope(Set(makeScopedAction(Domain.Scenes, Action.Share)))

  case object ScenesFullAccess
      extends ComplexScope(Set(ScenesCRUD, ScenesMultiPlayer))

  case object ProjectsCRUD
      extends SimpleScope(makeCRUDScopedActions(Domain.Projects))

  case object ProjectExport
      extends SimpleScope(Set(Action.ListExports, Action.CreateExport) map {
        makeScopedAction(Domain.Projects, _)
      })

  case object ProjectAnnotate
      extends SimpleScope(
        Set(
          Action.CreateAnnotation,
          Action.DeleteAnnotation,
          Action.UpdateAnnotation,
        ) map {
          makeScopedAction(Domain.Projects, _)
        }
      )

  case object DatasourcesCRUD
      extends SimpleScope(makeCRUDScopedActions(Domain.Datasources))

  case object ProjectsMultiPlayer
      extends SimpleScope(Set(makeScopedAction(Domain.Projects, Action.Share)))

  case object ProjectsFullAccess
      extends ComplexScope(
        Set(ProjectsCRUD, ProjectsMultiPlayer, ProjectExport, ProjectAnnotate)
      )

  case object ShapesCRUD
      extends SimpleScope(makeCRUDScopedActions(Domain.Shapes))

  case object ShapesMultiPlayer
      extends SimpleScope(Set(makeScopedAction(Domain.Shapes, Action.Share)))

  case object ShapesFullAccess
      extends ComplexScope(Set(ShapesCRUD, ShapesMultiPlayer))

  case object TemplatesCRUD
      extends SimpleScope(makeCRUDScopedActions(Domain.Templates))
  case object TemplatesMultiPlayer
      extends SimpleScope(Set(makeScopedAction(Domain.Templates, Action.Share)))
  case object TemplatesFullAccess
      extends ComplexScope(Set(TemplatesCRUD, TemplatesMultiPlayer))

  case object AnalysesCRUD
      extends SimpleScope(makeCRUDScopedActions(Domain.Analyses))
  case object AnalysesMultiPlayer
      extends SimpleScope(Set(makeScopedAction(Domain.Analyses, Action.Share)))
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
          new SimpleScope(Set(Domain.Teams, Domain.Organizations) flatMap {
            domain =>
              Set(
                makeScopedAction(domain, Action.Read),
                makeScopedAction(domain, Action.Read)
              )
          })
        )
      )

  case object TeamsUserAdmin
      extends SimpleScope(
        Set(Action.AddUser, Action.RemoveUser, Action.UpdateUserRole) map {
          makeScopedAction(Domain.Teams, _)
        }
      )

  case object OrganizationsUserAdmin
      extends SimpleScope(
        Set(Action.AddUser, Action.RemoveUser, Action.UpdateUserRole) map {
          makeScopedAction(Domain.Organizations, _)
        }
      )

  case object TeamsUpdate
      extends SimpleScope(
        Set(Action.Update, Action.Delete) map {
          makeScopedAction(Domain.Teams, _)
        }
      )

  case object RasterFoundryTeamAdmin
      extends ComplexScope(
        Set(
          RasterFoundryUser,
          TeamsUserAdmin,
          TeamsUpdate
        )
      )

  case object RasterFoundryOrganizationAdmin
      extends ComplexScope(
        Set(
          RasterFoundryTeamAdmin,
          OrganizationsUserAdmin,
          new SimpleScope(Set(makeScopedAction(Domain.Teams, Action.Create)))
        )
      )

  case object RasterFoundryPlatformAdmin
      extends ComplexScope(
        Set(
          RasterFoundryOrganizationAdmin,
          new SimpleScope(
            Set(makeScopedAction(Domain.Organizations, Action.Create)))
        )
      )
}
