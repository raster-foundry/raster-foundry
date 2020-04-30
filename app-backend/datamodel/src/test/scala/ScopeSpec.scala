package com.rasterfoundry.datamodel

import cats.Eq
import cats.kernel.laws.discipline.MonoidTests
import io.circe.parser._
import io.circe.testing.{ArbitraryInstances, CodecTests}
import org.scalacheck.{Arbitrary, Gen}
import org.scalatestplus.scalacheck.Checkers
import org.scalatest.funsuite.AnyFunSuite
import org.typelevel.discipline.scalatest.FunSuiteDiscipline

class ScopeSpec
    extends AnyFunSuite
    with FunSuiteDiscipline
    with Checkers
    with ArbitraryInstances {

  implicit val arbScopedAction: Arbitrary[ScopedAction] =
    Arbitrary[ScopedAction] {
      for {
        domain <- Gen.oneOf(
          Domain.Analyses,
          Domain.AnnotationGroups,
          Domain.AnnotationUploads,
          Domain.Datasources,
          Domain.Exports,
          Domain.FeatureFlags,
          Domain.Licenses,
          Domain.MapTokens,
          Domain.Organizations,
          Domain.Platforms,
          Domain.Projects,
          Domain.Scenes,
          Domain.Shapes,
          Domain.StacExports,
          Domain.Teams,
          Domain.Templates,
          Domain.Thumbnails,
          Domain.Tokens,
          Domain.Uploads,
          Domain.Users
        )
        action <- Gen.oneOf(
          Action.AddScenes,
          Action.AddUser,
          Action.ColorCorrect,
          Action.Create,
          Action.CreateAnnotation,
          Action.CreateExport,
          Action.CreateScopes,
          Action.CreateTaskGrid,
          Action.CreateTasks,
          Action.Delete,
          Action.DeleteAnnotation,
          Action.DeleteScopes,
          Action.DeleteTasks,
          Action.Download,
          Action.EditScenes,
          Action.ListExports,
          Action.ListUsers,
          Action.Read,
          Action.ReadPermissions,
          Action.ReadScopes,
          Action.ReadSelf,
          Action.ReadTasks,
          Action.ReadThumbnail,
          Action.ReadUsers,
          Action.RemoveUser,
          Action.Search,
          Action.Share,
          Action.Update,
          Action.UpdateAnnotation,
          Action.UpdateDropbox,
          Action.UpdateScopes,
          Action.UpdateSelf,
          Action.UpdateTasks,
          Action.UpdateUserRole
        )
        limit <- Arbitrary.arbitrary[Option[Long]]
      } yield ScopedAction(domain, action, limit)
    }

  def cannedPolicyGen: Gen[Scope] = Gen.oneOf(
    Scopes.AnalysesCRUD,
    Scopes.AnnotationGroupsCRUD,
    Scopes.AnnotationUploadsCRUD,
    Scopes.DatasourcesCRUD,
    Scopes.ExportsCRUD,
    Scopes.FeatureFlagsScope,
    Scopes.LicensesScope,
    Scopes.MapTokensCRUD,
    Scopes.OrganizationAdmin,
    Scopes.OrganizationsMember,
    Scopes.PlatformDomainAdminScope,
    Scopes.PlatformDomainMemberScope,
    Scopes.ProjectAnnotateScope,
    Scopes.ProjectsCRUD,
    Scopes.ShapesCRUD,
    Scopes.StacExportsCRUD,
    Scopes.ScenesCRUD,
    Scopes.TeamsCRUD,
    Scopes.TemplatesCRUD,
    Scopes.UploadsCRUD,
    Scopes.UsersAdminScope,
    Scopes.UserSelfScope,
    Scopes.RasterFoundryPlatformAdmin,
    Scopes.RasterFoundryUser,
    Scopes.RasterFoundryTeamsAdmin,
    Scopes.RasterFoundryOrganizationAdmin
  )

  // Not separating out into a separate object until we have more than
  // one of these. I don't think for the most part we depend on laws holding,
  // but in this case, since it determines user powers, I wanted the extra
  // security
  implicit val arbScope: Arbitrary[Scope] = Arbitrary[Scope] {
    for {
      scope <- Gen.oneOf(
        Arbitrary.arbitrary[Set[ScopedAction]] map { new SimpleScope(_) },
        cannedPolicyGen
      )
    } yield scope
  }

  checkAll("Scope.MonoidLaws", MonoidTests[Scope].monoid)
  checkAll("Scope.CodecTests", CodecTests[Scope].codec)

  test("decode a mix of simple and complex scopes") {
    val decoded = decode[Scope](""""projects:read;annotateTasks"""").right.get
    assert(
      Eq[Scope].eqv(
        decoded,
        new ComplexScope(
          Set(
            Scopes.AnnotateTasksScope,
            new SimpleScope(
              Set(ScopedAction(Domain.Projects, Action.Read, None))
            )
          )
        )
      )
    )
  }

  test("action resolution prefers ScopedActions without limits") {
    val action1 = ScopedAction(Domain.Projects, Action.Create, None)
    val action2 = ScopedAction(Domain.Projects, Action.Create, Some(10L))
    assert(
      Scopes
        .resolveFor(Domain.Projects, Action.Create, Set(action1, action2)) == Some(
        action1
      )
    )
  }

  test("action resolution prefers higher limits to lower limits") {
    val action1 = ScopedAction(Domain.Projects, Action.Create, Some(5L))
    val action2 = ScopedAction(Domain.Projects, Action.Create, Some(10L))
    assert(
      Scopes
        .resolveFor(Domain.Projects, Action.Create, Set(action1, action2)) == Some(
        action2
      )
    )
  }

  test("action resolution should not resolve missing actions") {
    val action = ScopedAction(Domain.Projects, Action.Create, Some(5L))
    assert(Scopes.resolveFor(Domain.Scenes, Action.Create, Set(action)).isEmpty)
  }
}
