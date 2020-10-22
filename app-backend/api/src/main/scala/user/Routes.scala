package com.rasterfoundry.api.user

import com.rasterfoundry.akkautil.PaginationDirectives
import com.rasterfoundry.akkautil.{
  Authentication,
  CommonHandlers,
  UserErrorHandler
}
import com.rasterfoundry.api.utils.Config
import com.rasterfoundry.api.utils.queryparams.QueryParametersCommon
import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.database._
import com.rasterfoundry.datamodel._
import com.rasterfoundry.datamodel.newtypes._

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import cats.effect.{Blocker, ContextShift, IO}
import cats.implicits._
import com.dropbox.core.{DbxAppInfo, DbxRequestConfig, DbxWebAuth}
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.Executors

/**
  * Routes for users
  */
trait UserRoutes
    extends Authentication
    with PaginationDirectives
    with CommonHandlers
    with UserErrorHandler
    with QueryParametersCommon
    with LazyLogging
    with Config {

  implicit val xa: Transactor[IO]

  implicit def contextShift: ContextShift[IO]

  val bulkUserCreateContext = ExecutionContext.fromExecutor(
    Executors.newFixedThreadPool(
      4,
      new ThreadFactoryBuilder().setNameFormat("bulk-user-create-%d").build()
    )
  )

  val userBulkCreateContextShift = IO.contextShift(bulkUserCreateContext)

  val userBulkCreateBlocker =
    Blocker.liftExecutionContext(bulkUserCreateContext)

  val userRoutes: Route = handleExceptions(userExceptionHandler) {
    pathPrefix("me") {
      pathPrefix("teams") {
        pathEndOrSingleSlash {
          get { getUserTeams }
        }
      } ~
        pathPrefix("roles") {
          get { getUserRoles }
        } ~
        pathPrefix("limits") {
          get { getUserLimits }
        } ~
        pathEndOrSingleSlash {
          get { getDbOwnUser } ~
            patch { updateAuth0User } ~
            put { updateOwnUser }
        }
    } ~
      pathPrefix("dropbox-setup") {
        pathEndOrSingleSlash {
          post { getDropboxAccessToken }
        }
      } ~
      pathPrefix("search") {
        pathEndOrSingleSlash {
          get { searchUsers }
        }
      } ~
      pathPrefix(Segment) { authIdEncoded =>
        pathEndOrSingleSlash {
          get { getUserByEncodedAuthId(authIdEncoded) } ~
            put { updateUserByEncodedAuthId(authIdEncoded) }
        }
      } ~
      pathPrefix("bulk-create") {
        pathEndOrSingleSlash {
          post { createUserBulk }
        } ~ pathPrefix(JavaUUID) { asyncCreateId =>
          pathEndOrSingleSlash {
            get {
              getAsyncUserBulkCreateJob(asyncCreateId)
            }
          }
        }
      }
  }

  def updateOwnUser: Route = authenticate { user =>
    authorizeScope(ScopedAction(Domain.Users, Action.UpdateSelf, None), user) {
      entity(as[User]) { userToUpdate =>
        if (userToUpdate.id == user.id) {
          onSuccess(
            UserDao.updateOwnUser(userToUpdate).transact(xa).unsafeToFuture()
          ) {
            completeSingleOrNotFound
          }
        } else {
          complete(StatusCodes.NotFound)
        }
      }
    }
  }

  def getDbOwnUser: Route = authenticate { user =>
    authorizeScope(ScopedAction(Domain.Users, Action.ReadSelf, None), user) {
      complete(
        UserDao
          .unsafeGetUserById(user.id, Some(true))
          .transact(xa)
          .unsafeToFuture()
      )
    }
  }

  def updateAuth0User: Route = authenticate { user =>
    authorizeScope(ScopedAction(Domain.Users, Action.UpdateSelf, None), user) {
      entity(as[Auth0UserUpdate]) { userUpdate =>
        complete {
          Auth0Service.updateAuth0User(user.id, userUpdate)
        }
      }
    }
  }

  def getDropboxAccessToken: Route = authenticate { user =>
    authorizeScope(ScopedAction(Domain.Users, Action.UpdateDropbox, None), user) {
      entity(as[DropboxAuthRequest]) { dbxAuthRequest =>
        val (dbxKey, dbxSecret) =
          (sys.env.get("DROPBOX_KEY"), sys.env.get("DROPBOX_SECRET")) match {
            case (Some(key), Some(secret)) => (key, secret)
            case _ =>
              throw new RuntimeException(
                "App dropbox credentials must be configured"
              )
          }
        val dbxConfig = new DbxRequestConfig("raster-foundry-authorizer")
        val appInfo = new DbxAppInfo(dbxKey, dbxSecret)
        val webAuth = new DbxWebAuth(dbxConfig, appInfo)
        val session = new DummySessionStore()
        val queryParams = Map[String, Array[String]](
          "code" -> Array(dbxAuthRequest.authorizationCode),
          "state" -> Array(session.get)
        ).asJava
        val authFinish = webAuth.finishFromRedirect(
          dbxAuthRequest.redirectURI,
          session,
          queryParams
        )
        logger.debug("Auth finish from Dropbox successful")
        complete(
          UserDao
            .storeDropboxAccessToken(
              user.id,
              Credential.fromString(authFinish.getAccessToken)
            )
            .transact(xa)
            .unsafeToFuture()
        )
      }
    }
  }

  def getUserByEncodedAuthId(authIdEncoded: String): Route = authenticate {
    user =>
      authorizeScope(ScopedAction(Domain.Users, Action.ReadSelf, None), user) {
        rejectEmptyResponse {
          val authId = URLDecoder.decode(authIdEncoded, "UTF-8")
          if (user.id == authId) {
            complete(
              UserDao.unsafeGetUserById(authId).transact(xa).unsafeToFuture()
            )
          } else if (user.id != authId) {
            complete(
              UserDao
                .unsafeGetUserById(authId, Some(false))
                .transact(xa)
                .unsafeToFuture()
            )
          } else {
            complete(StatusCodes.NotFound)
          }
        }
      }
  }

  def getUserTeams: Route = authenticate { user =>
    authorizeScope(ScopedAction(Domain.Users, Action.Read, None), user) {
      complete {
        TeamDao.teamsForUser(user).transact(xa).unsafeToFuture
      }
    }
  }

  def updateUserByEncodedAuthId(authIdEncoded: String): Route =
    authenticateSuperUser { user =>
      authorizeScope(ScopedAction(Domain.Users, Action.Update, None), user) {
        entity(as[User]) { updatedUser =>
          onSuccess(
            UserDao
              .updateUser(updatedUser, authIdEncoded)
              .transact(xa)
              .unsafeToFuture()
          ) {
            completeSingleOrNotFound
          }
        }
      }
    }

  def getUserRoles: Route = authenticate { user =>
    authorizeScope(ScopedAction(Domain.Users, Action.ReadSelf, None), user) {
      complete {
        UserGroupRoleDao
          .listByUserWithRelated(user)
          .transact(xa)
          .unsafeToFuture()
      }
    }
  }

  // Hard coded limits
  def getUserLimits: Route = authenticate { user =>
    authorizeScope(ScopedAction(Domain.Users, Action.ReadSelf, None), user) {
      val io = for {
        projectCount <- AnnotationProjectDao.countUserProjects(user)
        projectLimit = Scopes
          .resolveFor(
            Domain.AnnotationProjects,
            Action.Create,
            user.scope.actions
          )
          .flatMap(_.limit.map(_.toFloat))
        uploadBytes <- UploadDao.getUserBytesUploaded(user)
        uploadLimit = Scopes
          .resolveFor(Domain.Uploads, Action.Create, user.scope.actions)
          .flatMap(_.limit.map(_.toFloat))
        projectShares <- AnnotationProjectDao.getAllShareCounts(user.id)
        projectShareLimit = Scopes
          .resolveFor(
            Domain.AnnotationProjects,
            Action.Share,
            user.scope.actions
          )
          .flatMap(_.limit.map(_.toFloat))
      } yield
        List(
          ScopeUsage(
            Domain.AnnotationProjects,
            Action.Create,
            None,
            projectCount,
            projectLimit
          ),
          ScopeUsage(
            Domain.Uploads,
            Action.Create,
            None,
            uploadBytes,
            uploadLimit
          )
        ) ++ projectShares.toList.map {
          case (id, count) =>
            ScopeUsage(
              Domain.AnnotationProjects,
              Action.Share,
              Some(id.toString),
              count,
              projectShareLimit
            )
        }
      complete {
        io.transact(xa).unsafeToFuture
      }
    }
  }

  def searchUsers: Route = authenticate { user =>
    authorizeScope(ScopedAction(Domain.Users, Action.Search, None), user) {
      searchParams { (searchParams) =>
        complete {
          UserDao.searchUsers(user, searchParams).transact(xa).unsafeToFuture
        }
      }
    }
  }

  def createUserBulk: Route = authenticate { user =>
    authorizeScope(
      ScopedAction(Domain.Users, Action.BulkCreate, None),
      user
    ) {
      entity(as[UserBulkCreate]) { userBulkCreate =>
        complete {
          val names = PseudoUsernameService.createPseudoNames(
            userBulkCreate.count.value,
            userBulkCreate.peudoUserNameType
          )

          val userCreateIO =
            IO.fromFuture(IO(Auth0Service.bulkCreateUsers(names).map {
              // At this point, if we have an error we throw because the server should return a 500
              case Left(e)      => throw new Exception(e.error)
              case Right(users) => users
            }))

          val uwcsIO = for {
            users <- userCreateIO
            userWithCampaigns <- users traverse { auth0User =>
              for {
                userWithCampaign <- (auth0User.user_id, auth0User.username).tupled traverse {
                  case (userId, username) =>
                    UserDao
                      .createUserWithCampaign(
                        UserInfo(
                          userId,
                          s"${username}@$auth0AnonymizedConnectionName.com",
                          username
                        ),
                        userBulkCreate,
                        user
                      )
                      .transact(xa)
                }
              } yield userWithCampaign
            }
          } yield userWithCampaigns

          val bulkCreateIO =
            userBulkCreate.grantAccessToChildrenCampaignOwner match {
              case false =>
                uwcsIO
              case true =>
                for {
                  uwcs <- uwcsIO
                  _ <- (userBulkCreate.campaignId traverse { campaignId =>
                    CampaignDao
                      .grantCloneChildrenAccessById(campaignId, ActionType.View)
                  }).transact(xa)
                } yield uwcs

            }

          (for {
            asyncCreateJob <- AsyncBulkUserCreateDao
              .insertAsyncBulkUserCreate(userBulkCreate, user)
              .transact(xa)
            _ <- userBulkCreateBlocker
              .blockOn(bulkCreateIO)(userBulkCreateContextShift)
              .attempt
              .flatMap({
                case Right(users) =>
                  AsyncBulkUserCreateDao
                    .succeed(
                      asyncCreateJob.id,
                      new CreatedUserIds(users flatMap { userO =>
                        userO map { _.user.id }
                      })
                    )
                    .transact(xa)
                case Left(err) =>
                  AsyncBulkUserCreateDao
                    .fail(
                      asyncCreateJob.id,
                      new AsyncJobErrors(List(err.getMessage))
                    )
                    .transact(xa)
              })
              .start
          } yield asyncCreateJob).unsafeToFuture
        }
      }
    }
  }

  def getAsyncUserBulkCreateJob(jobId: UUID): Route = authenticate { user =>
    authorizeScope(ScopedAction(Domain.Users, Action.BulkCreate, None), user) {
      authorizeAsync {
        AsyncBulkUserCreateDao.query
          .filter(user)
          .exists
          .transact(xa)
          .unsafeToFuture
      } {
        complete {
          AsyncBulkUserCreateDao
            .getAsyncBulkUserCreate(jobId)
            .transact(xa)
            .unsafeToFuture
        }
      }
    }
  }
}
