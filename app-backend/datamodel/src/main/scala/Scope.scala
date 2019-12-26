package com.rasterfoundry.datamodel

import cats.{Eq, Monoid}

sealed abstract class Scope {
  val actions: Set[String]
}

class SimpleScope(val actions: Set[String]) extends Scope
object SimpleScope {
  def apply(actions: String*): SimpleScope =
    new SimpleScope(actions.toSet)
}

class ComplexScope(scopes: Set[Scope]) extends Scope {
  val actions = scopes flatMap { _.actions }
}
object ComplexScope {
  def apply(scopes: Scope*): ComplexScope =
    new ComplexScope(scopes.toSet)
}

object Scope {

  implicit val eqScope = new Eq[Scope] {
    def eqv(x: Scope, y: Scope): Boolean =
      x.actions == y.actions
  }

  implicit val monoidScope = new Monoid[Scope] {
    def empty = NoAccess
    def combine(x: Scope, y: Scope): Scope =
      ComplexScope(x, y)
  }

  private def makeCRUDScopes(prefix: String): Set[String] =
    Set(":read", ":create", ":update", ":delete") map { s =>
      prefix ++ s
    }

  // Separate scoped down upload permissions because most users don't need
  // to be able to edit uploads (since the system user does that in upload
  // processing)
  case object Uploader
      extends SimpleScope(
        Set("uploads:read", "uploads:create", "uploads:delete")
      )
  case object UploadsCRUD
      extends ComplexScope(Set(Uploader, SimpleScope("uploads:delete")))

  case object ScenesCRUD extends SimpleScope(makeCRUDScopes("scenes"))

  case object ProjectsCRUD extends SimpleScope(makeCRUDScopes("projects"))

  // Not creating a separate permission for getting an export's files, since
  // I can't currently imagine a case where someone would be allowed to view a
  // project's exports but not download their files
  case object ProjectExport
      extends SimpleScope(Set("projects:listExports", "projects:createExport"))

  case object ProjectsCRUDMultiPlayer
      extends ComplexScope(
        Set(ProjectsCRUD, SimpleScope("projects:share"))
      )

  case object ProjectsFullAccess
      extends ComplexScope(Set(ProjectsCRUDMultiPlayer, ProjectExport))

  // users with this scope aren't allowed to do anything
  case object NoAccess extends Scope {
    val actions = Set.empty
  }
}
