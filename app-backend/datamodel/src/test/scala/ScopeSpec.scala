package com.rasterfoundry.datamodel

import cats.kernel.laws.discipline.MonoidTests
import io.circe.testing.{ArbitraryInstances, CodecTests}
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.FunSuite
import org.scalatestplus.scalacheck.Checkers
import org.typelevel.discipline.scalatest.Discipline

class ScopeSpec
    extends FunSuite
    with Discipline
    with Checkers
    with ArbitraryInstances {

  implicit val arbAction: Arbitrary[Action] = Arbitrary[Action] {
    for {
      domain <- Gen.oneOf(
        "projects",
        "scenes",
        "shapes",
        "templates",
        "teams",
        "organizations",
        "datasources"
      )
      action <- Gen.oneOf(
        "read",
        "edit",
        "create",
        "delete"
      )
      limit <- Arbitrary.arbitrary[Option[Long]]
    } yield Action(domain, action, limit)
  }

  def cannedPolicyGen: Gen[Scope] = Gen.oneOf(
    Scopes.Uploader,
    Scopes.AnalysesCRUD,
    Scopes.AnalysesMultiPlayer,
    Scopes.DatasourcesCRUD,
    Scopes.OrganizationsUserAdmin,
    Scopes.ProjectExport,
    Scopes.ProjectsFullAccess,
    Scopes.RasterFoundryOrganizationAdmin,
    Scopes.RasterFoundryTeamAdmin,
    Scopes.ScenesCRUD,
    Scopes.ScenesMultiPlayer,
    Scopes.ShapesFullAccess,
    Scopes.TeamsEdit,
    Scopes.TemplatesCRUD,
    Scopes.TemplatesMultiPlayer,
    Scopes.UploadsCRUD
  )

  // Not separating out into a separate object until we have more than
  // one of these. I don't think for the most part we depend on laws holding,
  // but in this case, since it determines user powers, I wanted the extra
  // security
  implicit val arbScope: Arbitrary[Scope] = Arbitrary[Scope] {
    for {
      scope <- Gen.oneOf(
        Arbitrary.arbitrary[Set[Action]] map { new SimpleScope(_) },
        cannedPolicyGen
      )
    } yield scope
  }

  checkAll("Scope.MonoidLaws", MonoidTests[Scope].monoid)
  checkAll("Scope.CodecTests", CodecTests[Scope].codec)

  // TODO test some permissions relationships
}
