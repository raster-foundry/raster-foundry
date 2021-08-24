package com.rasterfoundry.datamodel

import com.rasterfoundry.datamodel.Implicits._

import cats.Eq
import cats.kernel.laws.discipline.MonoidTests
import io.circe.parser._
import io.circe.testing.{ArbitraryInstances, CodecTests}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.Checkers
import org.typelevel.discipline.scalatest.FunSuiteDiscipline

class ScopeSpec
    extends AnyFunSuite
    with FunSuiteDiscipline
    with Checkers
    with ArbitraryInstances {

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
        .resolveFor(
          Domain.Projects,
          Action.Create,
          Set(action1, action2)
        ) == Some(
        action1
      )
    )
  }

  test("action resolution prefers higher limits to lower limits") {
    val action1 = ScopedAction(Domain.Projects, Action.Create, Some(5L))
    val action2 = ScopedAction(Domain.Projects, Action.Create, Some(10L))
    assert(
      Scopes
        .resolveFor(
          Domain.Projects,
          Action.Create,
          Set(action1, action2)
        ) == Some(
        action2
      )
    )
  }

  test("action resolution should not resolve missing actions") {
    val action = ScopedAction(Domain.Projects, Action.Create, Some(5L))
    assert(Scopes.resolveFor(Domain.Scenes, Action.Create, Set(action)).isEmpty)
  }

  test("Groundwork users can create 10 campaigns") {
    assert(
      Scopes
        .resolveFor(
          Domain.Campaigns,
          Action.Create,
          Scopes.GroundworkUser.actions
        )
        .flatMap(_.limit) == Some(10L)
    )
  }

  test("Groundwork users can create unlimited annotation projects") {
    assert(
      Scopes
        .resolveFor(
          Domain.AnnotationProjects,
          Action.Create,
          Scopes.GroundworkUser.actions
        )
        .flatMap(_.limit) == None
    )
  }

  test("Groundwork Pro users can create 50 campaigns") {
    assert(
      Scopes
        .resolveFor(
          Domain.Campaigns,
          Action.Create,
          Scopes.GroundworkProUser.actions
        )
        .flatMap(_.limit) == Some(50L)
    )
  }

  test("Groundwork Pro users can share with 50 users per campaign") {
    assert(
      Scopes
        .resolveFor(
          Domain.Campaigns,
          Action.Share,
          Scopes.GroundworkProUser.actions
        )
        .flatMap(_.limit) == Some(50L)
    )
  }

  test("Groundwork Pro users can upload 50gb of data") {
    assert(
      Scopes
        .resolveFor(
          Domain.Uploads,
          Action.Create,
          Scopes.GroundworkProUser.actions
        )
        .flatMap(_.limit) == Some(50 * Scopes.oneGb)
    )
  }
}
