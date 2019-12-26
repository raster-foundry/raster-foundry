package com.rasterfoundry.datamodel

import cats.kernel.laws.discipline.MonoidTests
import org.scalacheck.Arbitrary
import org.scalatest.FunSuite
import org.typelevel.discipline.scalatest.Discipline

class ScopeSpec extends FunSuite with Discipline {

  // Not separating out into a separate object until we have more than
  // one of these. I don't think for the most part we depend on laws holding,
  // but in this case, since it determines user powers, I wanted the extra
  // security
  implicit val arbScope: Arbitrary[Scope] = Arbitrary[Scope] {
    for {
      actions <- Arbitrary.arbitrary[Set[String]]
    } yield new SimpleScope(actions)
  }

  checkAll("Scope.MonoidLaws", MonoidTests[Scope].monoid)

  // TODO test ser-de
  // TODO test some permissions relationships
}
