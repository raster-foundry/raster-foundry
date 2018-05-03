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
import com.lonelyplanet.akka.http.extensions.PageRequest

class TeamDaoSpec extends FunSuite with Matchers with Checkers with DBTestConfig with PropTestHelpers {

  test("listing teams") {
    TeamDao.query.list.transact(xa).unsafeRunSync.length >= 0
  }

  test("getting a team by ID") {
    check {
      forAll (
        (userCreate: User.Create, orgCreate: Organization.Create, teamCreate: Team.Create) => {
          val createTeamIO = for {
            orgAndUserInsert <- insertUserAndOrg(userCreate, orgCreate)
            (orgInsert, userInsert) = orgAndUserInsert
            teamInsert <- TeamDao.create(fixupTeam(teamCreate, orgInsert, userInsert))
          } yield (teamInsert, orgInsert)

          val getTeamAndOrgIO = createTeamIO flatMap {
            case (team: Team, org: Organization) => {
              TeamDao.getById(team.id) map { (_, org) }
            }
          }

          val (getTeamOp, org) = getTeamAndOrgIO.transact(xa).unsafeRunSync
          val getTeam = getTeamOp.get

          getTeam.name == teamCreate.name &&
            getTeam.organizationId == org.id &&
            getTeam.settings == teamCreate.settings &&
            getTeam.isActive == true
        }
      )
    }
  }

  test("getting a team by ID unsafely") {
    check {
      forAll (
        (userCreate: User.Create, orgCreate: Organization.Create, teamCreate: Team.Create) => {
          val createTeamIO = for {
            orgAndUserInsert <- insertUserAndOrg(userCreate, orgCreate)
            (orgInsert, userInsert) = orgAndUserInsert
            teamInsert <- TeamDao.create(fixupTeam(teamCreate, orgInsert, userInsert))
          } yield (teamInsert, orgInsert)

          val getTeamAndOrgIO = createTeamIO flatMap {
            case (team: Team, org: Organization) => {
              TeamDao.unsafeGetById(team.id) map { (_, org) }
            }
          }

          val (getTeam, org) = getTeamAndOrgIO.transact(xa).unsafeRunSync

          getTeam.name == teamCreate.name &&
            getTeam.organizationId == org.id &&
            getTeam.settings == teamCreate.settings &&
            getTeam.isActive == true
        }
      )
    }
  }

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
            createdTeam.settings == teamCreate.settings &&
            createdTeam.isActive == true
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
            updatedTeam.organizationId == org.id &&
            updatedTeam.isActive == true
        }
      )
    }
  }

  test("deleting a team by ID") {
    check {
      forAll (
        (userCreate: User.Create, orgCreate: Organization.Create, teamCreate: Team.Create) => {
          val createTeamIO = for {
            orgAndUserInsert <- insertUserAndOrg(userCreate, orgCreate)
            (orgInsert, userInsert) = orgAndUserInsert
            teamInsert <- TeamDao.create(fixupTeam(teamCreate, orgInsert, userInsert))
          } yield teamInsert

          val deleteTeamIO = createTeamIO flatMap {
            case (team: Team) => TeamDao.delete(team.id)
          }

          deleteTeamIO.transact(xa).unsafeRunSync == 1
        }
      )
    }
  }

}
