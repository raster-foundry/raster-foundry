package com.azavea.rf.datamodel

import com.azavea.rf.datamodel._

import org.scalacheck._
import org.scalacheck.Arbitrary.arbitrary

import java.sql.Timestamp
import java.time.LocalDate
import java.util.UUID

object Generators {

  def nonEmptyStringGen: Gen[String] =
    Gen.nonEmptyListOf[Char](Arbitrary.arbChar.arbitrary).map(_.mkString)

  def userRoleGen: Gen[UserRole] = Gen.oneOf(UserRoleRole, Viewer, Admin)

  def uuidGen: Gen[UUID] = Gen.delay(UUID.randomUUID)

  def timestampIn2016Gen: Gen[Timestamp] = for {
    year <- Gen.const(2016)
    month <- Gen.choose(1, 12)
    day <- Gen.choose(1, 28) // for safety
  } yield { Timestamp.valueOf(LocalDate.of(year, month, day).atStartOfDay) }

  def organizationCreateGen: Gen[Organization.Create] = for {
    name <- arbitrary[String]
  } yield (Organization.Create(name))

  def organizationGen: Gen[Organization] = organizationCreateGen map { _.toOrganization }

  def userCreateGen: Gen[User.Create] = for {
    id <- arbitrary[String]
    org <- organizationGen
    role <- userRoleGen
  } yield { User.Create(id, org.id, role) }

  def userGen: Gen[User] = userCreateGen map { _.toUser }

  def credentialGen: Gen[Credential] = nonEmptyStringGen map { Credential.fromString }

  object Implicits {
    implicit def arbOrganizationCreate: Arbitrary[Organization.Create] = Arbitrary { organizationCreateGen }

    implicit def arbOrganization: Arbitrary[Organization] = Arbitrary { organizationGen }

    implicit def arbCredential: Arbitrary[Credential] = Arbitrary { credentialGen }

    implicit def arbUserCreate: Arbitrary[User.Create] = Arbitrary { userCreateGen }

    implicit def arbUser: Arbitrary[User] = Arbitrary { userGen }
  }
}
