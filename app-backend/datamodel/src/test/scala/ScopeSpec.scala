package com.rasterfoundry.datamodel

import org.scalatest.FunSuite
import org.scalacheck.Arbitrary
import org.typelevel.discipline.scalatest.Discipline
import cats.kernel.laws.discipline.MonoidTests

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
}
