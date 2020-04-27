package com.rasterfoundry.datamodel

import cats.implicits._
import cats.{Eq, Monoid}
import io.circe.generic.semiauto._
import io.circe.parser._
import io.circe.{
  Decoder,
  DecodingFailure,
  Encoder,
  Error,
  HCursor,
  Json,
  ParsingFailure
}

import scala.util.{Failure, Success, Try}

sealed abstract class Domain(repr: String) {
  override def toString: String = repr
}

object Domain {
  implicit val domainEncoder: Encoder[Domain] =
    Encoder.encodeString.contramap[Domain] { domain =>
      domain.toString
    }

  case object Analyses extends Domain("analyses")
  case object AnnotationGroups extends Domain("annotationGroups")
  case object AnnotationUploads extends Domain("annotationUploads")
  case object Datasources extends Domain("datasources")
  case object Exports extends Domain("exports")
  case object FeatureFlags extends Domain("featureFlags")
  case object Licenses extends Domain("licenses")
  case object MapTokens extends Domain("mapTokens")
  case object Organizations extends Domain("organizations")
  case object Platforms extends Domain("platforms")
  case object Projects extends Domain("projects")
  case object Scenes extends Domain("scenes")
  case object Shapes extends Domain("shapes")
  case object StacExports extends Domain("stacExports")
  case object Teams extends Domain("teams")
  case object Templates extends Domain("templates")
  case object Thumbnails extends Domain("thumbnails")
  case object Tokens extends Domain("tokens")
  case object Uploads extends Domain("uploads")
  case object Users extends Domain("users")
  case object AnnotationProjects extends Domain("annotationProjects")
  case object Campaigns extends Domain("campaigns")

  def fromStringTry(s: String): Try[Domain] = s match {
    case "analyses"           => Success(Analyses)
    case "annotationGroups"   => Success(AnnotationGroups)
    case "annotationUploads"  => Success(AnnotationUploads)
    case "datasources"        => Success(Datasources)
    case "exports"            => Success(Exports)
    case "featureFlags"       => Success(FeatureFlags)
    case "licenses"           => Success(Licenses)
    case "mapTokens"          => Success(MapTokens)
    case "organizations"      => Success(Organizations)
    case "platforms"          => Success(Platforms)
    case "projects"           => Success(Projects)
    case "scenes"             => Success(Scenes)
    case "shapes"             => Success(Shapes)
    case "stacExports"        => Success(StacExports)
    case "teams"              => Success(Teams)
    case "templates"          => Success(Templates)
    case "thumbnails"         => Success(Thumbnails)
    case "tokens"             => Success(Tokens)
    case "uploads"            => Success(Uploads)
    case "users"              => Success(Users)
    case "annotationProjects" => Success(AnnotationProjects)
    case "campaigns"          => Success(Campaigns)
    case _                    => Failure(new Exception(s"Cannot parse Domain from string $s"))
  }
}

sealed abstract class Action(repr: String) {
  override def toString: String = repr
}

object Action {
  implicit val actionEncoder: Encoder[Action] =
    Encoder.encodeString.contramap[Action] { action =>
      action.toString
    }
  case object AddScenes extends Action("addScenes")
  case object AddUser extends Action("addUser")
  case object ColorCorrect extends Action("colorCorrect")
  case object Create extends Action("create")
  case object CreateAnnotation extends Action("createAnnotation")
  case object CreateExport extends Action("createExport")
  case object CreateScopes extends Action("createScopes")
  case object CreateTaskGrid extends Action("createTaskGrid")
  case object CreateTasks extends Action("createTasks")
  case object Delete extends Action("delete")
  case object DeleteAnnotation extends Action("deleteAnnotation")
  case object DeleteScopes extends Action("deleteScopes")
  case object DeleteTasks extends Action("deleteTasks")
  case object Download extends Action("download")
  case object EditScenes extends Action("editScenes")
  case object ListExports extends Action("listExports")
  case object ListUsers extends Action("listUsers")
  case object Read extends Action("read")
  case object ReadPermissions extends Action("readPermissions")
  case object ReadScopes extends Action("readScopes")
  case object ReadSelf extends Action("readSelf")
  case object ReadSentinelMetadata extends Action("readSentinelMetadata")
  case object ReadTasks extends Action("readTasks")
  case object ReadThumbnail extends Action("readThumbnail")
  case object ReadUsers extends Action("readUsers")
  case object RemoveUser extends Action("removeUser")
  case object Search extends Action("search")
  case object Share extends Action("share")
  case object Update extends Action("update")
  case object UpdateAnnotation extends Action("updateAnnotation")
  case object UpdateDropbox extends Action("updateDropbox")
  case object UpdateScopes extends Action("updateScopes")
  case object UpdateSelf extends Action("updateSelf")
  case object UpdateTasks extends Action("updateTasks")
  case object UpdateUserRole extends Action("updateUserRole")

  def fromStringTry(s: String): Try[Action] = s match {
    case "addScenes"            => Success(AddScenes)
    case "addUser"              => Success(AddUser)
    case "colorCorrect"         => Success(ColorCorrect)
    case "create"               => Success(Create)
    case "createAnnotation"     => Success(CreateAnnotation)
    case "createExport"         => Success(CreateExport)
    case "createScopes"         => Success(CreateScopes)
    case "createTaskGrid"       => Success(CreateTaskGrid)
    case "createTasks"          => Success(CreateTasks)
    case "delete"               => Success(Delete)
    case "deleteAnnotation"     => Success(DeleteAnnotation)
    case "deleteScopes"         => Success(DeleteScopes)
    case "deleteTasks"          => Success(DeleteTasks)
    case "download"             => Success(Download)
    case "editScenes"           => Success(EditScenes)
    case "listExports"          => Success(ListExports)
    case "listUsers"            => Success(ListUsers)
    case "read"                 => Success(Read)
    case "readUsers"            => Success(ReadUsers)
    case "readPermissions"      => Success(ReadPermissions)
    case "readScopes"           => Success(ReadScopes)
    case "readSelf"             => Success(ReadSelf)
    case "readSentinelMetadata" => Success(ReadSentinelMetadata)
    case "readTasks"            => Success(ReadTasks)
    case "readThumbnail"        => Success(ReadThumbnail)
    case "removeUser"           => Success(RemoveUser)
    case "search"               => Success(Search)
    case "share"                => Success(Share)
    case "update"               => Success(Update)
    case "updateAnnotation"     => Success(UpdateAnnotation)
    case "updateDropbox"        => Success(UpdateDropbox)
    case "updateScopes"         => Success(UpdateScopes)
    case "updateSelf"           => Success(UpdateSelf)
    case "updateTasks"          => Success(UpdateTasks)
    case "updateUserRole"       => Success(UpdateUserRole)
    case _                      => Failure(new Exception(s"Cannot parse Action from string $s"))
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
  val actions: Set[ScopedAction] = scopes flatMap { _.actions }
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
      case Scopes.RasterFoundryOrganizationAdmin =>
        Json.fromString("organizations:admin")
      case Scopes.RasterFoundryUser =>
        Json.fromString("platformUser")
      case Scopes.GroundworkUser => Json.fromString("groundworkUser")
      case Scopes.RasterFoundryPlatformAdmin =>
        Json.fromString("platforms:admin")
      case Scopes.RasterFoundryTeamsAdmin => Json.fromString("teams:admin")
      case Scopes.AnnotateTasksScope      => Json.fromString("annotateTasks")
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
      case Some(s) =>
        Scopes.cannedPolicyFromString(s).orElse {
          s.split(";").toList match {
            case List("") => Right(Monoid[Scope].empty)
            case actions =>
              Monoid[Either[Error, Scope]]
                .combineAll(
                  actions map { action =>
                    (decode[ScopedAction](s""""$action"""") map { scopedAct =>
                      new SimpleScope(Set(scopedAct))
                    }).orElse(Scopes.cannedPolicyFromString(action))
                  }
                )
                .leftMap(err => DecodingFailure(err.getMessage, Nil))
          }
        }
      case _ => Left(DecodingFailure("Scope", c.history))
    }
  }
}

object Scopes {

  def resolveFor(
      domain: Domain,
      action: Action,
      actions: Set[ScopedAction]
  ): Option[ScopedAction] =
    actions.foldLeft(Option.empty[ScopedAction])(
      (resolved: Option[ScopedAction], candidate: ScopedAction) => {
        if (candidate.domain != domain || candidate.action != action) {
          resolved
        } else {
          resolved map { resolvedAction =>
            (resolvedAction.limit, candidate.limit) match {
              case (Some(lim1), Some(lim2)) =>
                if (lim1 > lim2) resolvedAction else candidate
              case (Some(_), None) => candidate
              case (None, _)       => resolvedAction
            }
          } orElse { Some(candidate) }
        }
      }
    )

  def cannedPolicyFromString(s: String): Either[Error, Scope] = s match {
    case "organizations:admin" =>
      Right(Scopes.RasterFoundryOrganizationAdmin)
    case "platformUser"    => Right(Scopes.RasterFoundryUser)
    case "groundworkUser"  => Right(Scopes.GroundworkUser)
    case "platforms:admin" => Right(Scopes.RasterFoundryPlatformAdmin)
    case "teams:admin"     => Right(Scopes.RasterFoundryTeamsAdmin)
    case "annotateTasks"   => Right(Scopes.AnnotateTasksScope)
    case _ =>
      val message = s"$s is not a canned policy"
      Left(ParsingFailure(message, new Exception(message)))
  }

  private def makeCRUDScopedActions(domain: Domain): Set[ScopedAction] =
    Set(
      Action.Read,
      Action.Create,
      Action.Update,
      Action.Delete,
      Action.ReadPermissions,
      Action.Share
    ) map { s =>
      makeScopedAction(domain, s)
    }

  private def makeScopedAction(
      domain: Domain,
      action: Action,
      limit: Option[Long] = None
  ): ScopedAction =
    ScopedAction(domain, action, limit)

  case object NoAccess extends SimpleScope(Set.empty)

  case object AnalysesCRUD
      extends SimpleScope(makeCRUDScopedActions(Domain.Analyses))

  case object AnnotationGroupsCRUD
      extends SimpleScope(makeCRUDScopedActions(Domain.AnnotationGroups))

  case object AnnotationUploadsCRUD
      extends SimpleScope(makeCRUDScopedActions(Domain.AnnotationUploads))

  case object DatasourcesCRUD
      extends SimpleScope(makeCRUDScopedActions(Domain.Datasources))

  case object ExportsCRUD
      extends SimpleScope(makeCRUDScopedActions(Domain.Exports))

  case object FeatureFlagsScope
      extends SimpleScope(
        Set(ScopedAction(Domain.FeatureFlags, Action.Read, None))
      )

  case object LicensesScope
      extends SimpleScope(Set(ScopedAction(Domain.Licenses, Action.Read, None)))

  case object MapTokensCRUD
      extends SimpleScope(makeCRUDScopedActions(Domain.MapTokens))

  case object OrganizationAdmin
      extends SimpleScope(
        Set(
          ScopedAction(Domain.Organizations, Action.AddUser, None),
          ScopedAction(Domain.Organizations, Action.Update, None)
        )
      )

  case object OrganizationsMember
      extends SimpleScope(
        Set(Action.ListUsers, Action.Read, Action.Search)
          .map(makeScopedAction(Domain.Organizations, _))
      )

  case object PlatformDomainAdminScope
      extends SimpleScope(
        Set(
          ScopedAction(Domain.Organizations, Action.Create, None),
          ScopedAction(Domain.Organizations, Action.Delete, None),
          ScopedAction(Domain.Platforms, Action.AddUser, None),
          ScopedAction(Domain.Platforms, Action.Create, None),
          ScopedAction(Domain.Platforms, Action.Delete, None),
          ScopedAction(Domain.Platforms, Action.ListUsers, None),
          ScopedAction(Domain.Platforms, Action.Update, None)
        )
      )

  case object PlatformDomainMemberScope
      extends SimpleScope(
        Set(ScopedAction(Domain.Platforms, Action.Read, None))
      )

  case object ProjectAnnotateScope
      extends SimpleScope(
        Set(
          ScopedAction(Domain.Projects, Action.CreateAnnotation, None),
          ScopedAction(Domain.Projects, Action.DeleteAnnotation, None),
          ScopedAction(Domain.Projects, Action.UpdateAnnotation, None)
        )
      )

  case object AnnotateTasksScope
      extends SimpleScope(
        Set(
          ScopedAction(Domain.Projects, Action.CreateTaskGrid, None),
          ScopedAction(Domain.Projects, Action.CreateTasks, None),
          ScopedAction(Domain.Projects, Action.DeleteTasks, None),
          ScopedAction(Domain.Projects, Action.UpdateTasks, None),
          ScopedAction(Domain.Projects, Action.ReadTasks, None)
        )
      )

  case object ProjectsCRUD
      extends SimpleScope(
        Set(
          Action.AddScenes,
          Action.ColorCorrect,
          Action.EditScenes
        ).map(makeScopedAction(Domain.Projects, _)) ++ makeCRUDScopedActions(
          Domain.Projects
        )
      )

  case object ScenesCRUD
      extends SimpleScope(
        Set(
          Action.Download,
          Action.ReadThumbnail,
          Action.ReadSentinelMetadata
        ).map(makeScopedAction(Domain.Scenes, _)) ++ makeCRUDScopedActions(
          Domain.Scenes
        )
      )

  case object ThumbnailScope
      extends SimpleScope(
        Set(ScopedAction(Domain.Thumbnails, Action.Read, None))
      )

  case object ShapesCRUD
      extends SimpleScope(makeCRUDScopedActions(Domain.Shapes))

  case object StacExportsCRUD
      extends SimpleScope(makeCRUDScopedActions(Domain.StacExports))

  case object TeamsCRUD
      extends SimpleScope(
        Set(ScopedAction(Domain.Teams, Action.ListUsers, None)) ++ makeCRUDScopedActions(
          Domain.Teams
        )
      )

  case object TemplatesCRUD
      extends SimpleScope(makeCRUDScopedActions(Domain.Templates))

  case object TokensCRUD
      extends SimpleScope(
        Set(
          Action.Delete,
          Action.Read
        ).map(makeScopedAction(Domain.Tokens, _))
      )

  case object UploadsCRUD
      extends SimpleScope(makeCRUDScopedActions(Domain.Uploads))

  case object UsersAdminScope
      extends SimpleScope(
        Set(
          Action.CreateScopes,
          Action.DeleteScopes,
          Action.Read,
          Action.ReadScopes,
          Action.Search,
          Action.Update,
          Action.UpdateScopes
        ).map(makeScopedAction(Domain.Users, _))
      )

  case object UserSelfScope
      extends SimpleScope(
        Set(Action.ReadSelf, Action.UpdateSelf, Action.UpdateDropbox)
          .map(makeScopedAction(Domain.Users, _))
      )

  // Canned Scopes used in Database Migration
  case object RasterFoundryUser
      extends ComplexScope(
        Set(
          AnalysesCRUD,
          AnnotationGroupsCRUD,
          AnnotationUploadsCRUD,
          DatasourcesCRUD,
          ExportsCRUD,
          FeatureFlagsScope,
          LicensesScope,
          MapTokensCRUD,
          OrganizationsMember,
          PlatformDomainMemberScope,
          ProjectsCRUD,
          ScenesCRUD,
          ShapesCRUD,
          StacExportsCRUD,
          TeamsCRUD,
          TemplatesCRUD,
          ThumbnailScope,
          TokensCRUD,
          UploadsCRUD,
          UserSelfScope,
          ProjectAnnotateScope
        )
      )

  case object RasterFoundryTeamsAdmin
      extends SimpleScope(
        RasterFoundryUser.actions
      )

  case object RasterFoundryOrganizationAdmin
      extends ComplexScope(
        Set(RasterFoundryUser, OrganizationAdmin, UsersAdminScope)
      )

  case object RasterFoundryPlatformAdmin
      extends ComplexScope(
        Set(
          RasterFoundryOrganizationAdmin,
          PlatformDomainAdminScope
        )
      )

  case object GroundworkUser
      extends SimpleScope(
        Set(
          ScopedAction(Domain.Licenses, Action.Read, None),
          ScopedAction(Domain.Scenes, Action.Read, None),
          ScopedAction(Domain.Uploads, Action.Read, None),
          ScopedAction(
            Domain.Uploads,
            Action.Create,
            Some((10 * scala.math.pow(2, 30)).toLong)
          ),
          ScopedAction(Domain.Uploads, Action.Update, None),
          ScopedAction(Domain.Uploads, Action.Delete, None),
          ScopedAction(Domain.Uploads, Action.ReadPermissions, None),
          ScopedAction(Domain.Uploads, Action.Share, None),
          ScopedAction(
            Domain.AnnotationProjects,
            Action.Create,
            Some(10.toLong)
          ),
          ScopedAction(Domain.AnnotationProjects, Action.Share, Some(5.toLong)),
          ScopedAction(Domain.Projects, Action.Create, None),
          ScopedAction(
            Domain.Campaigns,
            Action.Create,
            Some(10.toLong)
          ),
          ScopedAction(Domain.Campaigns, Action.Share, Some(5.toLong))
        ) ++ Set(
          Action.Read,
          Action.Update,
          Action.Delete,
          Action.CreateAnnotation,
          Action.DeleteAnnotation,
          Action.UpdateAnnotation,
          Action.CreateTaskGrid,
          Action.CreateTasks,
          Action.DeleteTasks,
          Action.UpdateTasks,
          Action.ReadTasks,
          Action.AddScenes,
          Action.ReadPermissions
        ).map(makeScopedAction(Domain.AnnotationProjects, _, None)) ++
          Set(
            Action.Read,
            Action.Update,
            Action.Delete,
            Action.CreateAnnotation,
            Action.DeleteAnnotation,
            Action.UpdateAnnotation,
            Action.CreateTaskGrid,
            Action.CreateTasks,
            Action.DeleteTasks,
            Action.UpdateTasks,
            Action.ReadTasks,
            Action.AddScenes,
            Action.ReadPermissions
          ).map(makeScopedAction(Domain.Campaigns, _, None)) ++
          StacExportsCRUD.actions ++
          UserSelfScope.actions ++
          FeatureFlagsScope.actions
      )
}

case class ScopeUsage(
    domain: Domain,
    action: Action,
    objectId: Option[String],
    used: Float,
    limit: Option[Float]
)

object ScopeUsage {
  implicit val scopeUsageEncoder: Encoder[ScopeUsage] =
    deriveEncoder[ScopeUsage]
}
