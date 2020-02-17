package com.rasterfoundry.api.platform

import com.rasterfoundry.akkautil.PaginationDirectives
import com.rasterfoundry.akkautil.{
  Authentication,
  CommonHandlers,
  UserErrorHandler
}
import com.rasterfoundry.api.utils.queryparams.QueryParametersCommon
import com.rasterfoundry.database.filter.Filterables._
import com.rasterfoundry.database.{
  OrganizationDao,
  PlatformDao,
  TeamDao,
  UserDao
}
import com.rasterfoundry.datamodel._

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import cats.effect.IO
import cats.implicits._
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie._
import doobie.implicits._

import java.util.UUID

trait PlatformRoutes
    extends Authentication
    with PaginationDirectives
    with CommonHandlers
    with UserErrorHandler
    with QueryParametersCommon {

  val xa: Transactor[IO]

  val platformRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get {
        listPlatforms
      } ~
        post {
          createPlatform
        }
    } ~
      pathPrefix(JavaUUID) { platformId =>
        pathEndOrSingleSlash {
          get {
            getPlatform(platformId)
          } ~
            put {
              updatePlatform(platformId)
            } ~
            post {
              setPlatformStatus(platformId)
            }
        } ~
          pathPrefix("members") {
            validate(
              PlatformDao.validatePath(platformId).transact(xa).unsafeRunSync,
              "Resource path invalid"
            ) {
              pathEndOrSingleSlash {
                get {
                  listPlatformMembers(platformId)
                }
              }
            }
          } ~
          pathPrefix("teams") {
            validate(
              PlatformDao.validatePath(platformId).transact(xa).unsafeRunSync,
              "Resource path invalid"
            ) {
              pathPrefix("search") {
                pathEndOrSingleSlash {
                  get {
                    listPlatformUserTeams(platformId)
                  }
                }
              }
            }
          } ~
          pathPrefix("organizations") {
            pathEndOrSingleSlash {
              validate(
                PlatformDao.validatePath(platformId).transact(xa).unsafeRunSync,
                "Resource path invalid"
              ) {
                get {
                  listPlatformOrganizations(platformId)
                } ~
                  post {
                    createOrganization(platformId)
                  }
              }
            } ~
              pathPrefix(JavaUUID) { orgId =>
                pathEndOrSingleSlash {
                  validate(
                    OrganizationDao
                      .validatePath(platformId, orgId)
                      .transact(xa)
                      .unsafeRunSync,
                    "Resource path invalid"
                  ) {
                    get {
                      getOrganization(platformId, orgId)
                    } ~
                      put {
                        updateOrganization(orgId)
                      } ~
                      post {
                        setOrganizationStatus(platformId, orgId)
                      }
                  }
                } ~
                  pathPrefix("members") {
                    pathEndOrSingleSlash {
                      validate(
                        OrganizationDao
                          .validatePath(platformId, orgId)
                          .transact(xa)
                          .unsafeRunSync,
                        "Resource path invalid"
                      ) {
                        get {
                          listOrganizationMembers(orgId)
                        } ~
                          post {
                            addUserToOrganization(platformId, orgId)
                          }
                      }
                    } ~
                      pathPrefix(Segment) { userId =>
                        delete {
                          removeUserFromOrganization(orgId, userId)
                        }
                      }
                  } ~
                  pathPrefix("teams") {
                    pathEndOrSingleSlash {
                      validate(
                        OrganizationDao
                          .validatePath(platformId, orgId)
                          .transact(xa)
                          .unsafeRunSync,
                        "Resource path invalid"
                      ) {
                        get {
                          listTeams(orgId)
                        } ~
                          post {
                            createTeam(orgId)
                          }
                      }
                    } ~
                      pathPrefix(JavaUUID) { teamId =>
                        pathEndOrSingleSlash {
                          validate(
                            TeamDao
                              .validatePath(platformId, orgId, teamId)
                              .transact(xa)
                              .unsafeRunSync,
                            "Resource path invalid"
                          ) {
                            get {
                              getTeam(orgId, teamId)
                            } ~
                              put {
                                updateTeam(teamId)
                              } ~
                              delete {
                                deleteTeam(teamId)
                              }
                          }
                        } ~
                          pathPrefix("members") {
                            validate(
                              TeamDao
                                .validatePath(platformId, orgId, teamId)
                                .transact(xa)
                                .unsafeRunSync,
                              "Resource path invalid"
                            ) {
                              pathEndOrSingleSlash {
                                get {
                                  listTeamMembers(orgId, teamId)
                                } ~
                                  post {
                                    addUserToTeam(platformId, teamId)
                                  }
                              } ~
                                pathPrefix(Segment) { userId =>
                                  delete {
                                    removeUserFromTeam(teamId, userId)
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
    authorizeScope(ScopedAction(Domain.Platforms, Action.Read, None), user) {
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
  }

  def createPlatform: Route = authenticate { user =>
    authorizeScope(ScopedAction(Domain.Platforms, Action.Create, None), user) {
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
  }

  def getPlatform(platformId: UUID): Route = authenticate { user =>
    authorizeScope(ScopedAction(Domain.Platforms, Action.Read, None), user) {
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
  }

  def updatePlatform(platformId: UUID): Route = authenticate { user =>
    authorizeScope(ScopedAction(Domain.Platforms, Action.Update, None), user) {
      authorizeAsync {
        PlatformDao.userIsAdmin(user, platformId).transact(xa).unsafeToFuture
      } {
        entity(as[Platform]) { platformToUpdate =>
          completeWithOneOrFail {
            PlatformDao
              .update(platformToUpdate, platformId)
              .transact(xa)
              .unsafeToFuture
          }
        }
      }
    }
  }

  def listPlatformMembers(platformId: UUID): Route = authenticate { user =>
    authorizeScope(
      ScopedAction(Domain.Platforms, Action.ListUsers, None),
      user
    ) {
      authorizeAsync {
        PlatformDao.userIsAdmin(user, platformId).transact(xa).unsafeToFuture
      } {
        (withPagination & searchParams) { (page, searchParams) =>
          complete {
            PlatformDao
              .listMembers(platformId, page, searchParams, user)
              .transact(xa)
              .unsafeToFuture
          }
        }
      }
    }
  }

  // List teams:
  // - the operating user belongs to
  // - the operating user can see due to organization membership
  // - limited to first 5 ordered by team name
  // - filtered by `search=<team name>` if specified
  def listPlatformUserTeams(platformId: UUID): Route = authenticate { user =>
    authorizeScope(ScopedAction(Domain.Teams, Action.Search, None), user) {
      authorizeAsync {
        PlatformDao.userIsMember(user, platformId).transact(xa).unsafeToFuture
      } {
        (searchParams) { (searchParams) =>
          complete {
            PlatformDao
              .listPlatformUserTeams(user, searchParams)
              .transact(xa)
              .unsafeToFuture
          }
        }
      }
    }
  }

  def listPlatformOrganizations(platformId: UUID): Route = authenticate {
    user =>
      authorizeScope(
        ScopedAction(Domain.Organizations, Action.Read, None),
        user
      ) {
        authorizeAsync {
          PlatformDao.userIsAdmin(user, platformId).transact(xa).unsafeToFuture
        } {
          (withPagination & searchParams) { (page, search) =>
            complete {
              OrganizationDao
                .listPlatformOrganizations(page, search, platformId, user)
                .transact(xa)
                .unsafeToFuture
            }
          }
        }
      }
  }

  def createOrganization(platformId: UUID): Route = authenticate { user =>
    authorizeScope(
      ScopedAction(Domain.Organizations, Action.Create, None),
      user
    ) {
      entity(as[Organization.Create]) { orgToCreate =>
        completeOrFail {
          val createdOrg = for {
            isAdmin <- PlatformDao.userIsAdmin(user, platformId)
            org <- OrganizationDao.create(orgToCreate.toOrganization(isAdmin))
          } yield org
          createdOrg.transact(xa).unsafeToFuture
        }
      }
    }
  }

  def getOrganization(platformId: UUID, orgId: UUID): Route = authenticate {
    user =>
      authorizeScope(
        ScopedAction(Domain.Organizations, Action.Read, None),
        user
      ) {
        authorizeAsync {
          PlatformDao.userIsMember(user, platformId).transact(xa).unsafeToFuture
        } {
          rejectEmptyResponse {
            complete {
              OrganizationDao.query
                .filter(orgId)
                .selectOption
                .transact(xa)
                .unsafeToFuture
            }
          }
        }
      }
  }

  def updateOrganization(orgId: UUID): Route = authenticate { user =>
    authorizeScope(
      ScopedAction(Domain.Organizations, Action.Update, None),
      user
    ) {
      authorizeAsync {
        OrganizationDao.userIsAdmin(user, orgId).transact(xa).unsafeToFuture
      } {
        entity(as[Organization]) { orgToUpdate =>
          completeWithOneOrFail {
            OrganizationDao
              .update(orgToUpdate, orgId)
              .transact(xa)
              .unsafeToFuture
          }
        }
      }
    }
  }

  def listOrganizationMembers(orgId: UUID): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.Organizations, Action.ListUsers, None),
        user
      ) {
        authorizeAsync {
          // QUESTION: should this be open to members of the same platform?
          OrganizationDao.userIsMember(user, orgId).transact(xa).unsafeToFuture
        } {
          (withPagination & searchParams) { (page, searchParams) =>
            complete {
              OrganizationDao
                .listMembers(orgId, page, searchParams, user)
                .transact(xa)
                .unsafeToFuture
            }
          }
        }
      }
    }

  def addUserToOrganization(platformId: UUID, orgId: UUID): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.Organizations, Action.AddUser, None),
        user
      ) {
        entity(as[UserGroupRole.UserRole]) { ur =>
          authorizeAsync {
            val authCheck = (
              OrganizationDao.userIsAdmin(user, orgId),
              PlatformDao.userIsMember(user, platformId),
              (user.id == ur.userId).pure[ConnectionIO]
            ).tupled.map(
              {
                case (true, _, _) | (_, true, true) => true
                case _                              => false
              }
            )
            authCheck.transact(xa).unsafeToFuture
          } {
            complete {
              OrganizationDao
                .addUserRole(platformId, user, ur.userId, orgId, ur.groupRole)
                .transact(xa)
                .unsafeToFuture
            }
          }
        }
      }
    }

  def removeUserFromOrganization(orgId: UUID, userId: String): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.Organizations, Action.RemoveUser, None),
        user
      ) {
        authorizeAsync {
          val authCheck = (
            OrganizationDao.userIsAdmin(user, orgId),
            (userId == user.id).pure[ConnectionIO]
          ).tupled.map(
            {
              case (true, _) | (_, true) => true
              case _                     => false
            }
          )
          authCheck.transact(xa).unsafeToFuture
        } {
          complete {
            OrganizationDao
              .deactivateUserRoles(userId, orgId)
              .transact(xa)
              .unsafeToFuture
          }
        }
      }
    }

  def listTeams(organizationId: UUID): Route = authenticate { user =>
    authorizeScope(ScopedAction(Domain.Teams, Action.Read, None), user) {
      authorizeAsync {
        OrganizationDao
          .userIsMember(user, organizationId)
          .transact(xa)
          .unsafeToFuture
      } {
        (withPagination & teamQueryParameters) { (page, teamQueryParams) =>
          complete {
            TeamDao
              .listOrgTeams(organizationId, page, teamQueryParams)
              .transact(xa)
              .unsafeToFuture
          }
        }
      }
    }
  }

  def createTeam(orgId: UUID): Route = authenticate { user =>
    authorizeScope(ScopedAction(Domain.Teams, Action.Create, None), user) {
      authorizeAsync {
        OrganizationDao.userIsMember(user, orgId).transact(xa).unsafeToFuture
      } {
        entity(as[Team.Create]) { newTeamCreate =>
          onSuccess(
            OrganizationDao
              .userIsAdmin(user, orgId)
              .flatMap {
                case true => TeamDao.create(newTeamCreate.toTeam(user))
                case _ =>
                  TeamDao.createWithRole(
                    newTeamCreate.toTeam(user),
                    user
                  )
              }
              .transact(xa)
              .unsafeToFuture()
          ) { team =>
            complete(StatusCodes.Created, team)
          }
        }
      }
    }
  }

  def getTeam(orgId: UUID, teamId: UUID): Route =
    authenticate { user =>
      authorizeScope(ScopedAction(Domain.Teams, Action.Read, None), user) {
        authorizeAsync {
          OrganizationDao.userIsMember(user, orgId).transact(xa).unsafeToFuture
        } {
          rejectEmptyResponse {
            complete {
              TeamDao.query
                .filter(teamId)
                .selectOption
                .transact(xa)
                .unsafeToFuture
            }
          }
        }
      }
    }

  def updateTeam(teamId: UUID): Route =
    authenticate { user =>
      authorizeScope(ScopedAction(Domain.Teams, Action.Update, None), user) {
        authorizeAsync {
          TeamDao.userIsAdmin(user, teamId).transact(xa).unsafeToFuture
        } {
          entity(as[Team]) { updatedTeam =>
            onSuccess {
              TeamDao
                .update(updatedTeam, teamId)
                .transact(xa)
                .unsafeToFuture
            } { team =>
              complete(StatusCodes.OK, team)
            }
          }
        }
      }
    }

  def deleteTeam(teamId: UUID): Route =
    authenticate { user =>
      authorizeScope(ScopedAction(Domain.Teams, Action.Delete, None), user) {
        authorizeAsync {
          TeamDao.userIsAdmin(user, teamId).transact(xa).unsafeToFuture
        } {
          completeWithOneOrFail {
            TeamDao.deactivate(teamId).transact(xa).unsafeToFuture
          }
        }
      }
    }

  def listTeamMembers(orgId: UUID, teamId: UUID): Route =
    authenticate { user =>
      authorizeScope(ScopedAction(Domain.Teams, Action.ListUsers, None), user) {
        authorizeAsync {
          // The authorization here is necessary to allow users within cross-organizational teams to view
          // the members within those teams
          val decisionIO = for {
            isTeamMember <- TeamDao.userIsMember(user, teamId)
            isOrganizationMember <- OrganizationDao.userIsMember(user, orgId)
          } yield {
            isTeamMember || isOrganizationMember
          }

          decisionIO.transact(xa).unsafeToFuture
        } {
          (withPagination & searchParams) { (page, searchParams) =>
            complete {
              TeamDao
                .listMembers(teamId, page, searchParams, user)
                .transact(xa)
                .unsafeToFuture
            }
          }
        }
      }
    }

  def addUserToTeam(platformId: UUID, teamId: UUID): Route =
    authenticate { user =>
      authorizeScope(ScopedAction(Domain.Teams, Action.AddUser, None), user) {
        entity(as[UserGroupRole.UserRole]) { ur =>
          authorizeAsync {
            val authCheck = (
              TeamDao.userIsAdmin(user, teamId),
              PlatformDao.userIsMember(user, platformId),
              (user.id == ur.userId).pure[ConnectionIO]
            ).tupled.map(
              {
                case (true, _, _) | (_, true, true) => true
                case _                              => false
              }
            )
            authCheck.transact(xa).unsafeToFuture
          } {
            complete {
              TeamDao
                .addUserRole(platformId, user, ur.userId, teamId, ur.groupRole)
                .transact(xa)
                .unsafeToFuture
            }
          }
        }
      }
    }

  def removeUserFromTeam(teamId: UUID, userId: String): Route = authenticate {
    user =>
      authorizeScope(ScopedAction(Domain.Teams, Action.RemoveUser, None), user) {
        authorizeAsync {
          val authCheck = (
            TeamDao.userIsAdmin(user, teamId),
            (userId == user.id).pure[ConnectionIO]
          ).tupled.map(
            {
              case (true, _) | (_, true) => true
              case _                     => false
            }
          )
          authCheck.transact(xa).unsafeToFuture
        } {
          complete {
            TeamDao
              .deactivateUserRoles(userId, teamId)
              .transact(xa)
              .unsafeToFuture
          }
        }
      }
  }

  def setPlatformStatus(platformId: UUID): Route = authenticate { user =>
    authorizeScope(ScopedAction(Domain.Platforms, Action.Update, None), user) {
      authorizeAsync {
        PlatformDao.userIsAdmin(user, platformId).transact(xa).unsafeToFuture
      } {
        entity(as[ActiveStatus]) {
          case ActiveStatus(true) =>
            activatePlatform(platformId, user)
          case ActiveStatus(false) =>
            deactivatePlatform(platformId)
        }
      }
    }
  }

  def activatePlatform(platformId: UUID, user: User): Route =
    authorizeScope(ScopedAction(Domain.Platforms, Action.Update, None), user) {
      authorizeAsync {
        UserDao.isSuperUser(user).transact(xa).unsafeToFuture
      } {
        complete {
          PlatformDao.activatePlatform(platformId).transact(xa).unsafeToFuture
        }
      }
    }

  def deactivatePlatform(platformId: UUID): Route = complete {
    PlatformDao.deactivatePlatform(platformId).transact(xa).unsafeToFuture
  }

  def setOrganizationStatus(platformId: UUID, organizationId: UUID): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.Organizations, Action.Update, None),
        user
      ) {
        authorizeAsync {
          OrganizationDao
            .userIsAdmin(user, organizationId)
            .transact(xa)
            .unsafeToFuture
        } {
          entity(as[String]) {
            case status: String if status == OrgStatus.Active.toString =>
              activateOrganization(platformId, organizationId, user)
            case status: String if status == OrgStatus.Inactive.toString =>
              deactivateOrganization(organizationId, user)
          }
        }
      }
    }

  def activateOrganization(
      platformId: UUID,
      organizationId: UUID,
      user: User
  ): Route =
    authorizeScope(
      ScopedAction(Domain.Organizations, Action.Update, None),
      user
    ) {
      authorizeAsync {
        PlatformDao.userIsAdmin(user, platformId).transact(xa).unsafeToFuture
      } {
        complete {
          OrganizationDao
            .activateOrganization(organizationId)
            .transact(xa)
            .unsafeToFuture
        }
      }
    }

  def deactivateOrganization(organizationId: UUID, user: User): Route =
    authorizeScope(
      ScopedAction(Domain.Organizations, Action.Update, None),
      user
    ) {
      authorizeAsync {
        OrganizationDao
          .userIsAdmin(user, organizationId)
          .transact(xa)
          .unsafeToFuture
      } {
        complete {
          OrganizationDao
            .deactivateOrganization(organizationId)
            .transact(xa)
            .unsafeToFuture
        }
      }
    }
}
