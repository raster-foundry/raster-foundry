package com.rasterfoundry.datamodel

import cats.{Eq, Monoid}

case class Action(domain: String, action: String, limit: Option[Long])

sealed abstract class Scope {
  val actions: Set[Action]
}

sealed class SimpleScope(
    val actions: Set[Action]
) extends Scope

sealed class ComplexScope(scopes: Set[Scope]) extends Scope {
  val actions = scopes flatMap { _.actions }
}

object ComplexScope {
  private[datamodel] def apply(scopes: Scope*): ComplexScope =
    new ComplexScope(scopes.toSet)
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
        Set("addUser", "removeUser", "promoteUser") map {
          makeAction("teams", _)
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

  // TODO
  case object RasterFoundryOrganizationAdmin
      extends ComplexScope(
        Set(
          RasterFoundryTeamAdmin
        )
      )

  // TODO
  case object RasterFoundryPlatformAdmin
      extends ComplexScope(
        Set(
          RasterFoundryOrganizationAdmin
        )
      )
}
