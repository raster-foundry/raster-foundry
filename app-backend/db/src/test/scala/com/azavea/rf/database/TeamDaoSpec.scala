package com.azavea.rf.database

import java.sql.Timestamp

import com.azavea.rf.datamodel._
import com.azavea.rf.datamodel.Generators.Implicits._
import com.azavea.rf.database.Implicits._
import doobie._
import doobie.implicits._
import cats._
import cats.data._
import cats.effect.IO
import cats.syntax.either._
import doobie.postgres._
import doobie.postgres.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatest.prop.Checkers
import io.circe._
import io.circe.syntax._
import java.util.UUID

class TeamDaoSpec extends FunSuite with Matchers with Checkers with DBTestConfig with PropTestHelpers {

  test("creating a team") {
    check {
      forAll (
        (userCreate: User.Create, orgCreate: Organization.Create, teamCreate: Team.Create) => {
          val createTeamIO = for {
            orgAndUserInsert <- insertUserAndOrg(userCreate, orgCreate)
            (orgInsert, userInsert) = orgAndUserInsert
            teamInsert <- TeamDao.create(fixupTeam(teamCreate, orgInsert, userInsert))
          } yield (teamInsert, orgInsert)

          val (createdTeam, org) = createTeamIO.transact(xa).unsafeRunSync

          createdTeam.name == teamCreate.name &&
            createdTeam.organizationId == org.id &&
            createdTeam.settings == teamCreate.settings
        }
      )
    }
  }

  test("updating a team"){
    check {
      forAll (
        (userCreate: User.Create, orgCreate: Organization.Create, teamCreate: Team.Create, teamUpdate: Team.Create) => {
          val createTeamIO = for {
            orgAndUserInsert <- insertUserAndOrg(userCreate, orgCreate)
            (orgInsert, userInsert) = orgAndUserInsert
            teamInsert <- TeamDao.create(fixupTeam(teamCreate, orgInsert, userInsert))
          } yield (teamInsert, orgInsert, userInsert)

          val updateTeamIO = createTeamIO flatMap {
            case (team: Team, org: Organization, user: User) => {
              TeamDao.update(
                fixupTeam(teamUpdate, org, user),
                team.id,
                user
              ) map {
                (_, org)
              }
            }
          }

          val (updatedTeam, org) = updateTeamIO.transact(xa).unsafeRunSync

          updatedTeam.name == teamUpdate.name &&
            updatedTeam.settings == teamUpdate.settings &&
            updatedTeam.organizationId == org.id
        }
      )
    }
  }

}
