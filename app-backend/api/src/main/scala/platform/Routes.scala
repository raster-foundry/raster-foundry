package com.azavea.rf.api.platform

import com.azavea.rf.common.{Authentication, UserErrorHandler, CommonHandlers}
import com.azavea.rf.database.{PlatformDao, OrganizationDao, TeamDao, UserDao, UserGroupRoleDao}
import com.azavea.rf.datamodel._
import com.azavea.rf.database.filter.Filterables._
import com.azavea.rf.api.utils.queryparams.QueryParametersCommon

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import cats.effect.IO
import cats.implicits._
import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import io.circe._
import kamon.akka.http.KamonTraceDirectives

import scala.util.{Failure, Success}

import java.util.UUID

trait PlatformRoutes extends Authentication
  with PaginationDirectives
  with CommonHandlers
  with KamonTraceDirectives
  with UserErrorHandler
  with QueryParametersCommon {

  val xa: Transactor[IO]

  val platformRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get {
        traceName("platforms-list") {
          listPlatforms
        }
      } ~
      post {
        traceName("platforms-create") {
          createPlatform
        }
      }
    } ~
    pathPrefix(JavaUUID) { platformId =>
      pathEndOrSingleSlash {
        get {
          traceName("platforms-get") {
            getPlatform(platformId)
          }
        } ~
        put {
          traceName("platforms-update") {
            updatePlatform(platformId)
          }
        } ~
        delete {
          traceName("platforms-delete") {
            deletePlatform(platformId)
          }
        }
      } ~
      pathPrefix("members") {
        validate (
          PlatformDao.validatePath(platformId).transact(xa).unsafeRunSync,
          "Resource path invalid"
        ) {
          pathEndOrSingleSlash {
            get {
              traceName("platforms-members-list") {
                listPlatformMembers(platformId)
              }
            }
          } ~
          post {
            traceName("platforms-member-add") {
              addUserToPlatform(platformId)
            }
          }
        }
      } ~
      pathPrefix("organizations") {
        pathEndOrSingleSlash {
          validate (
            PlatformDao.validatePath(platformId).transact(xa).unsafeRunSync,
            "Resource path invalid"
          ) {
            get {
              traceName("platforms-organizations-list") {
                listOrganizations(platformId)
              }
            } ~
            post {
              traceName("platforms-organizations-create") {
                createOrganization(platformId)
              }
            }
          }
        } ~
        pathPrefix(JavaUUID) { orgId =>
          pathEndOrSingleSlash {
            validate (
              OrganizationDao.validatePath(platformId, orgId).transact(xa).unsafeRunSync,
              "Resource path invalid"
            ) {
              get {
                traceName("platforms-organizations-get") {
                  getOrganization(platformId, orgId)
                }
              } ~
              put {
                traceName("platforms-organizations-update") {
                  updateOrganization(platformId, orgId)
                }
              } ~
              delete {
                traceName("platforms-organizations-delete") {
                  deleteOrganization(platformId, orgId)
                }
              }
            }
          } ~
          pathPrefix("members") {
            pathEndOrSingleSlash {
              validate (
                OrganizationDao.validatePath(platformId, orgId).transact(xa).unsafeRunSync,
                "Resource path invalid"
              ) {
                get {
                  traceName("platforms-organizations-members-list") {
                    listOrganizationMembers(platformId, orgId)
                  }
                } ~
                post {
                  traceName("platform-organizations-member-add") {
                    addUserToOrganization(platformId, orgId)
                  }
                }
              }
            }
          } ~
          pathPrefix("teams") {
            pathEndOrSingleSlash {
              validate (
                OrganizationDao.validatePath(platformId, orgId).transact(xa).unsafeRunSync,
                "Resource path invalid"
              ) {
                get {
                  traceName("platforms-organizations-teams-list") {
                    listTeams(platformId, orgId)
                  }
                } ~
                post {
                  traceName("platforms-organizations-teams-create") {
                    createTeam(platformId, orgId)
                  }
                }
              }
            } ~
            pathPrefix(JavaUUID) { teamId =>
              pathEndOrSingleSlash {
                validate (
                  TeamDao.validatePath(platformId, orgId, teamId).transact(xa).unsafeRunSync,
                  "Resource path invalid"
                ) {
                  get {
                    traceName("platforms-organizations-teams-get") {
                      getTeam(platformId, orgId, teamId)
                    }
                  } ~
                  put {
                    traceName("platforms-organizations-teams-update") {
                      updateTeam(platformId, orgId, teamId)
                    }
                  } ~
                  delete {
                    traceName("platforms-organizations-teams-delete") {
                      deleteTeam(platformId, orgId, teamId)
                    }
                  }
                }
              } ~
              pathPrefix("members") {
                validate (
                  TeamDao.validatePath(platformId, orgId, teamId).transact(xa).unsafeRunSync,
                  "Resource path invalid"
                ) {
                  pathEndOrSingleSlash {
                    get {
                      traceName("platforms-organizations-teams-members-list") {
                        listTeamMembers(platformId, orgId, teamId)
                      }
                    } ~
                    post {
                      traceName("platform-organizations-teams-member-add") {
                        addUserToTeam(platformId, orgId, teamId)
                      }
                    }
                  } ~
                  pathPrefix(Segment) { userId =>
                    delete {
                      traceName("platform-organizations-teams-member-remove") {
                        removeUserFromTeam(platformId, orgId, teamId, userId)
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  // @TODO: most platform API interactions should be highly restricted -- only 'super-users' should
  // be able to do list, create, update, delete. Non-super users can only get a platform if they belong to it.
  def listPlatforms: Route = authenticate { user =>
    authorizeAsync {
      UserDao.isSuperUser(user).transact(xa).unsafeToFuture
    } {
      withPagination { page =>
        complete {
          PlatformDao.listPlatforms(page).transact(xa).unsafeToFuture
        }
      }
    }
  }

  def createPlatform: Route = authenticate { user =>
    authorizeAsync {
      UserDao.isSuperUser(user).transact(xa).unsafeToFuture
    } {
      entity(as[Platform]) { platform =>
        completeOrFail {
          PlatformDao.create(platform).transact(xa).unsafeToFuture
        }
      }
    }
  }

  def getPlatform(platformId: UUID): Route = authenticate { user =>
    authorizeAsync {
      PlatformDao.userIsMember(user, platformId).transact(xa).unsafeToFuture
    } {
      rejectEmptyResponse {
        complete {
          PlatformDao.getPlatformById(platformId).transact(xa).unsafeToFuture
        }
      }
    }
  }

  def updatePlatform(platformId: UUID): Route = authenticate { user =>
   authorizeAsync {
      PlatformDao.userIsAdmin(user, platformId).transact(xa).unsafeToFuture
    } {
      entity(as[Platform]) { platformToUpdate =>
        completeWithOneOrFail {
          PlatformDao.update(platformToUpdate, platformId, user).transact(xa).unsafeToFuture
        }
      }
    }
  }

  // @TODO: We may want to remove this functionality and instead deactivate platforms
  def deletePlatform(platformId: UUID): Route = authenticate { user =>
    authorizeAsync {
      UserDao.isSuperUser(user).transact(xa).unsafeToFuture
    } {
      completeWithOneOrFail {
        ???
      }
    }
  }

  def listPlatformMembers(platformId: UUID): Route = authenticate { user =>
    authorizeAsync {
      // QUESTION: should this be open to members of the same platform?
      PlatformDao.userIsAdmin(user, platformId).transact(xa).unsafeToFuture
    } {
      (withPagination & searchParams) { (page, searchParams) =>
        complete {
          PlatformDao.listMembers(platformId, page, searchParams, user).transact(xa).unsafeToFuture
        }
      }
    }
  }

  def addUserToPlatform(platformId: UUID): Route = authenticate { user =>
    authorizeAsync {
      PlatformDao.userIsAdmin(user, platformId).transact(xa).unsafeToFuture
    } {
      entity(as[UserGroupRole.UserRole]) { ur =>
        complete {
          PlatformDao.setUserRole(user, ur.userId, platformId, ur.groupRole).transact(xa).unsafeToFuture
        }
      }
    }
  }

  def listOrganizations(platformId: UUID): Route = authenticate { user =>
    authorizeAsync {
      PlatformDao.userIsMember(user, platformId).transact(xa).unsafeToFuture
    } {
      withPagination { page =>
        complete {
          OrganizationDao.query.page(page).transact(xa).unsafeToFuture
        }
      }
    }
  }

  def createOrganization(platformId: UUID): Route = authenticate { user =>
    authorizeAsync {
      PlatformDao.userIsAdmin(user, platformId).transact(xa).unsafeToFuture
    } {
      entity(as[Organization.Create]) { orgToCreate =>
        completeOrFail {
          OrganizationDao.create(orgToCreate.toOrganization).transact(xa).unsafeToFuture
        }
      }
    }
  }

  def getOrganization(platformId: UUID, orgId: UUID): Route = authenticate { user =>
    authorizeAsync {
      PlatformDao.userIsMember(user, platformId).transact(xa).unsafeToFuture
    } {
      rejectEmptyResponse {
        complete {
          OrganizationDao.query.filter(orgId).selectOption.transact(xa).unsafeToFuture
        }
      }
    }
  }

  def updateOrganization(platformId: UUID, orgId: UUID): Route = authenticate { user =>
    authorizeAsync {
      OrganizationDao.userIsAdmin(user, orgId).transact(xa).unsafeToFuture
    } {
      entity(as[Organization]) { orgToUpdate =>
        completeWithOneOrFail {
          OrganizationDao.update(orgToUpdate, orgId).transact(xa).unsafeToFuture
        }
      }
    }
  }

  def deleteOrganization(platformId: UUID, orgId: UUID): Route = authenticate { user =>
    authorizeAsync {
      PlatformDao.userIsAdmin(user, platformId).transact(xa).unsafeToFuture
    } {
      completeWithOneOrFail {
        ???
      }
    }
  }

  def listOrganizationMembers(platformId: UUID, orgId: UUID): Route = authenticate { user =>
    authorizeAsync {
      // QUESTION: should this be open to members of the same platform?
      OrganizationDao.userIsMember(user, orgId).transact(xa).unsafeToFuture
    } {
      (withPagination & searchParams) { (page, searchParams) =>
        complete {
          OrganizationDao.listMembers(orgId, page, searchParams, user).transact(xa).unsafeToFuture
        }
      }
    }
  }

  def addUserToOrganization(platformId: UUID, orgId: UUID): Route = authenticate { user =>
    authorizeAsync {
      OrganizationDao.userIsAdmin(user, orgId).transact(xa).unsafeToFuture
    } {
      entity(as[UserGroupRole.UserRole]) { ur =>
        complete {
          OrganizationDao.setUserRole(user, ur.userId, orgId, ur.groupRole).transact(xa).unsafeToFuture
        }
      }
    }
  }

  def listTeams(platformId: UUID, orgId: UUID): Route = authenticate { user =>
    authorizeAsync {
      OrganizationDao.userIsMember(user, orgId).transact(xa).unsafeToFuture
    } {
      withPagination { page =>
        complete {
          TeamDao.query.filter(fr"organization_id = ${orgId}").page(page).transact(xa).unsafeToFuture
        }
      }
    }
  }

  def createTeam(platformId: UUID, orgId: UUID): Route = authenticate { user =>
    authorizeAsync {
      // QUESTION: do we want to allow any user to create a team rather than only admins?
      OrganizationDao.userIsAdmin(user, orgId).transact(xa).unsafeToFuture
    } {
      entity(as[Team.Create]) { newTeamCreate =>
        onSuccess(TeamDao.create(newTeamCreate.toTeam(user)).transact(xa).unsafeToFuture) { team =>
          complete(StatusCodes.Created, team)
        }
      }
    }
  }

  def getTeam(platformId: UUID, orgId: UUID, teamId: UUID): Route = authenticate { user =>
    authorizeAsync {
      OrganizationDao.userIsMember(user, orgId).transact(xa).unsafeToFuture
    } {
      rejectEmptyResponse {
        complete {
          TeamDao.query.filter(teamId).selectOption.transact(xa).unsafeToFuture
        }
      }
    }
  }

  def updateTeam(platformId: UUID, orgId: UUID, teamId: UUID): Route = authenticate { user =>
    authorizeAsync {
      TeamDao.userIsAdmin(user, teamId).transact(xa).unsafeToFuture
    } {
      entity(as[Team]) { updatedTeam =>
        onSuccess {
          TeamDao.update(updatedTeam, teamId, user).transact(xa).unsafeToFuture
        } { team =>
          complete(StatusCodes.OK, team)
        }
      }
    }
  }

  def deleteTeam(platformId: UUID, orgId: UUID, teamId: UUID): Route = authenticate { user =>
    authorizeAsync {
      TeamDao.userIsAdmin(user, teamId).transact(xa).unsafeToFuture
    } {
      completeWithOneOrFail {
        ???
      }
    }
  }

  def listTeamMembers(platformId: UUID, orgId: UUID, teamId: UUID): Route = authenticate { user =>
    authorizeAsync {
      // The authorization here is necessary to allow users within cross-organizational teams to view
      // the members within those teams
      val decisionIO = for {
        isTeamMember <- TeamDao.userIsMember(user, teamId)
        isOrganizationMember <- OrganizationDao.userIsMember(user, orgId)
      } yield { isTeamMember || isOrganizationMember }

      decisionIO.transact(xa).unsafeToFuture
    } {
      (withPagination & searchParams) { (page, searchParams) =>
        complete {
          TeamDao.listMembers(teamId, page, searchParams, user).transact(xa).unsafeToFuture
        }
      }
    }
  }

  def addUserToTeam(platformId: UUID, orgId: UUID, teamId: UUID): Route = authenticate { user =>
    authorizeAsync {
      TeamDao.userIsAdmin(user, teamId).transact(xa).unsafeToFuture
    } {
      entity(as[UserGroupRole.UserRole]) { ur =>
        complete {
          TeamDao.setUserRole(user, ur.userId, teamId, ur.groupRole).transact(xa).unsafeToFuture
        }
      }
    }
  }

  def removeUserFromTeam(platformId: UUID, orgId: UUID, teamId: UUID, userId: String): Route = authenticate { user =>
    authorizeAsync {
      TeamDao.userIsAdmin(user, teamId).transact(xa).unsafeToFuture
    } {
      complete {
        TeamDao.deactivateUserRoles(user, userId, teamId).transact(xa).unsafeToFuture
      }
    }
  }
}
