package com.azavea.rf.database

import java.sql.Timestamp

import com.azavea.rf.datamodel._
import com.azavea.rf.datamodel.Generators.Implicits._
import com.azavea.rf.database.Implicits._
import com.azavea.rf.database._
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
              TeamDao.getTeamById(team.id) map { (_, org) }
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
              TeamDao.unsafeGetTeamById(team.id) map { (_, org) }
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

  // ACR deactivation upon team deactivation needs to be reconsidered in issue 4020
  test("Deactivated teams are not listed") {
    check {
      forAll (
        (userCreate: User.Create, orgCreate: Organization.Create, teamCreate: Team.Create,
         acr: ObjectAccessControlRule, project: Project.Create) => {
          val createTeamIO = for {
            orgAndUserInsert <- insertUserAndOrg(userCreate, orgCreate)
            (orgInsert, userInsert) = orgAndUserInsert
            teamInsert <- TeamDao.create(fixupTeam(teamCreate, orgInsert, userInsert))
            insertedProject <- ProjectDao.insertProject(project, userInsert)
            acrToInsert = acr.copy(subjectType = SubjectType.Team, subjectId = Some(teamInsert.id.toString()))
            _ <- ProjectDao.addPermission(insertedProject.id, acrToInsert)
            deactivateTeam <- TeamDao.deactivate(teamInsert.id)
            permissionAfterTeamDeactivate <- ProjectDao.getPermissions(insertedProject.id)
            deactivatedTeams <- TeamDao.query.filter(fr"is_active = false").filter(fr"modified_by=${userInsert.id}").list
            activatedTeams <- TeamDao.listOrgTeams(orgInsert.id, PageRequest(0, 30, Map.empty), TeamQueryParameters())
          } yield { (deactivatedTeams, activatedTeams, acrToInsert, permissionAfterTeamDeactivate) }
          val (deactivatedTeams, activatedTeams, acrToInsert, permissionAfterTeamDeactivate) = createTeamIO.transact(xa).unsafeRunSync

          assert(deactivatedTeams.size == 1, "Deactivated team should exist")
          assert(activatedTeams.results.size == 0, "No team is active")
          assert(Set(acrToInsert) == permissionAfterTeamDeactivate.flatten.toSet, "Permissions exists after team deactivation")
          true
        }
      )
    }
  }

  test("add a user role") {
    check {
      forAll{
        (
          userCreate: User.Create, orgCreate: Organization.Create, teamCreate: Team.Create,
          userRole: GroupRole
        ) => {
          val addUserTeamRoleIO = for {
            orgAndUser <- insertUserAndOrg(userCreate, orgCreate)
            (org, dbUser) = orgAndUser
            insertedTeam <- TeamDao.create(fixupTeam(teamCreate, org, dbUser))
            insertedUserGroupRole <- TeamDao.addUserRole(org.platformId, dbUser, dbUser.id, insertedTeam.id, userRole)
            byIdUserGroupRole <- UserGroupRoleDao.getOption(insertedUserGroupRole.id)
          } yield { (insertedTeam, byIdUserGroupRole) }

          val (dbTeam, dbUserGroupRole) = addUserTeamRoleIO.transact(xa).unsafeRunSync
          dbUserGroupRole match {
            case Some(ugr) =>
              assert(ugr.isActive, "; Added role should be active")
              assert(ugr.groupType == GroupType.Team, "; Added role should be for a Team")
              assert(ugr.groupId == dbTeam.id, "; Added role should be for the correct Team")
              assert(ugr.groupRole == userRole, "; Added role should have the correct role")
              true
            case _ => false
          }
        }
      }
    }
  }

  test("deactivate a user's roles") {
    check {
      forAll{
        (
          userCreate: User.Create, orgCreate: Organization.Create, teamCreate: Team.Create,
          userRole: GroupRole
        ) => {
          val setTeamRoleIO = for {
            orgAndUser <- insertUserAndOrg(userCreate, orgCreate)
            (org, dbUser) = orgAndUser
            insertedTeam <- TeamDao.create(fixupTeam(teamCreate, org, dbUser))
            originalUserGroupRole <- TeamDao.addUserRole(org.platformId, dbUser, dbUser.id, insertedTeam.id, userRole)
            updatedUserGroupRoles <- TeamDao.deactivateUserRoles(dbUser, dbUser.id, insertedTeam.id)
          } yield { (originalUserGroupRole, updatedUserGroupRoles ) }

          val (dbOldUGR, dbNewUGRs) = setTeamRoleIO.transact(xa).unsafeRunSync

          assert(dbNewUGRs.filter((ugr) => ugr.isActive == false).size == 1,
                 "; The updated UGR should be inactive")
          assert(dbNewUGRs.size == 1, "; There should only be a single UGR updated")
          true
        }
      }
    }
  }

  test("listing teams for a user") {
    check {
      forAll {
        (platform: Platform, userCreate: User.Create, orgCreate: Organization.Create,
         teamCreate1: Team.Create, teamCreate2: Team.Create) => {
          val groupRole = GroupRole.Member
          val teamsForUserIO = for {
            userOrgPlatform <- insertUserOrgPlatform(userCreate, orgCreate, platform)
            (dbUser, dbOrg, dbPlatform) = userOrgPlatform
            team1 <- TeamDao.create(fixupTeam(teamCreate1, dbOrg, dbUser))
            team2 <- TeamDao.create(fixupTeam(teamCreate2, dbOrg, dbUser))
            _ <- UserGroupRoleDao.create(
              UserGroupRole.Create(
                dbUser.id, GroupType.Team, team1.id, groupRole
              ).toUserGroupRole(dbUser, MembershipStatus.Approved)
            )
            _ <- UserGroupRoleDao.create(
              UserGroupRole.Create(
                dbUser.id, GroupType.Team, team2.id, groupRole
              ).toUserGroupRole(dbUser, MembershipStatus.Approved)
            )
            listedTeams <- TeamDao.teamsForUser(dbUser)
          } yield { (List(team1, team2), listedTeams) }
          val (insertedTeams, listedTeams) = teamsForUserIO.transact(xa).unsafeRunSync
          assert(insertedTeams.map( _.name ).toSet == listedTeams.map( _.name ).toSet,
                 "Inserted and listed teams for this user should be the same")
          true
        }
      }
    }
  }

}
