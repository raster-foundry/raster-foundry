package com.rasterfoundry.datamodel

import cats.{Eq, Monoid}

sealed abstract class Scope {
  val actions: Set[String]
}

sealed abstract class QuantitativeLimit {
  val limit: Option[Long]
}

sealed class SimpleScope(val actions: Set[String]) extends Scope
object SimpleScope {
  private[datamodel] def apply(actions: String*): SimpleScope =
    new SimpleScope(actions.toSet)
}

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

  private def makeCRUDScopes(prefix: String): Set[String] =
    Set(":read", ":create", ":update", ":delete") map { s =>
      prefix ++ s
    }

  case object Uploader
      extends SimpleScope(
        Set("uploads:read", "uploads:create", "uploads:delete")
      )

  case object UploadsCRUD
      extends ComplexScope(Set(Uploader, SimpleScope("uploads:delete")))

  case object ScenesCRUD extends SimpleScope(makeCRUDScopes("scenes"))

  case object ScenesMultiPlayer extends SimpleScope(Set("scenes:share"))

  case object ScenesFullAccess
      extends ComplexScope(Set(ScenesCRUD, ScenesMultiPlayer))

  case object ProjectsCRUD extends SimpleScope(makeCRUDScopes("projects"))

  case object ProjectExport
      extends SimpleScope(Set("projects:listExports", "projects:createExport"))

  case object ProjectAnnotate
      extends SimpleScope(
        Set(
          "projects:createAnnotation",
          "projects:deleteAnnotation",
          "projects:editAnnotation"
        )
      )

  case object DatasourcesCRUD extends SimpleScope(makeCRUDScopes("datasources"))

  case object ProjectsMultiPlayer extends SimpleScope(Set("projects:share"))

  case object ProjectsFullAccess
      extends ComplexScope(
        Set(ProjectsCRUD, ProjectsMultiPlayer, ProjectExport, ProjectAnnotate)
      )

  case object ShapesCRUD extends SimpleScope(makeCRUDScopes("shapes"))

  case object ShapesMultiPlayer extends SimpleScope(Set("shapes:share"))

  case object ShapesFullAccess
      extends ComplexScope(Set(ShapesCRUD, ShapesMultiPlayer))

  case object TemplatesCRUD extends SimpleScope(makeCRUDScopes("templates"))
  case object TemplatesMultiPlayer extends SimpleScope(Set("templates:share"))
  case object TemplatesFullAccess
      extends ComplexScope(Set(TemplatesCRUD, TemplatesMultiPlayer))

  case object AnalysesCRUD extends SimpleScope(makeCRUDScopes("analyses"))
  case object AnalysesMultiPlayer extends SimpleScope(Set("analyses:share"))
  case object AnalysesFullAccess
      extends ComplexScope(Set(AnalysesCRUD, AnalysesMultiPlayer))

  case object NoAccess extends SimpleScope(Set.empty)

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
          SimpleScope("teams:read", "organizations:read")
        )
      )
}
