package com.rasterfoundry.akkautil

import com.rasterfoundry.datamodel.{
  Action,
  AuthResult,
  Domain,
  ScopedAction,
  Scope,
  Scopes,
  User
}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives._
import akka.http.scaladsl.server.directives.HeaderDirectives._
import cats.implicits._
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import io.circe._
import io.circe.syntax._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

trait CommonHandlers extends RouteDirectives {

  implicit val ec: ExecutionContext

  private val simulationHeaderName = "X-PolicySim"
  private val includeScopesHeaderName = "X-PolicySim-Include"
  private val excludeScopesHeaderName = "X-PolicySim-Exclude"

  def completeWithOneOrFail(
      future: ⇒ Future[Int]
  ): RequestContext => Future[RouteResult] =
    FutureDirectives.onComplete(future) {
      case Success(count) => completeSingleOrNotFound(count)
      case Failure(err)   => failWith(err)
    }

  def completeOrFail(
      magnet: CompleteOrRecoverWithMagnet
  ): RequestContext => Future[RouteResult] =
    FutureDirectives.completeOrRecoverWith(magnet)(failWith)

  def completeSingleOrNotFound(count: Int): StandardRoute = count match {
    case 1 => complete(StatusCodes.NoContent)
    case 0 => complete(StatusCodes.NotFound)
    case _ =>
      throw new IllegalStateException(s"Result expected to be 1, was $count")
  }

  def completeSomeOrNotFound(count: Int): StandardRoute = count match {
    case 0          => complete(StatusCodes.NotFound)
    case x if x > 0 => complete(StatusCodes.NoContent)
    case _ =>
      throw new IllegalStateException(
        s"Result expected to be 0 or positive, was $count"
      )
  }

  def maybeSim(scopedAction: ScopedAction,
               user: User,
               fallback: => Directive0): Directive0 =
    (optionalHeaderValueByName(simulationHeaderName) &
      optionalHeaderValueByName(includeScopesHeaderName) &
      optionalHeaderValueByName(excludeScopesHeaderName)).tflatMap({
      case (Some("true"), include, exclude) =>
        val extraScope = Decoder[Scope].decodeJson(include.asJson) getOrElse {
          Scopes.NoAccess
        }
        val withoutScope = Decoder[Scope].decodeJson(exclude.asJson) getOrElse {
          Scopes.NoAccess
        }
        val userActions =
          (user.scope `combine` extraScope).actions.diff(withoutScope.actions)
        complete {
          Map("simResult" -> userActions.contains(scopedAction)).asJson
        }
      case _ =>
        fallback
    })

  def authorizeScope(scopedAction: ScopedAction, user: User): Directive0 =
    maybeSim(scopedAction,
             user,
             SecurityDirectives.authorize(
               !Scopes
                 .resolveFor(scopedAction.domain,
                             scopedAction.action,
                             user.scope.actions)
                 .isEmpty))

  def authorizeScopeLimitDirective(
      usedLimitFuture: Future[Long],
      domain: Domain,
      action: Action,
      user: User
  ): Directive0 = {
    val userScopedAction = Scopes.resolveFor(domain, action, user.scope.actions)
    val userLimitOption = userScopedAction.flatMap(_.limit)

    // if user does not have an explicit scope limit, assume infinite
    val isBelowLimit = usedLimitFuture.map { usedLimit =>
      (userScopedAction, userLimitOption) match {
        case (None, _) => false
        case (_, Some(userLimit)) =>
          usedLimit < userLimit
        case _ => true
      }
    }
    SecurityDirectives.authorizeAsync(isBelowLimit)
  }

  def authorizeScopeLimit(
      usedLimitFuture: Future[Long],
      domain: Domain,
      action: Action,
      user: User
  ): Directive0 =
    maybeSim(
      ScopedAction(domain, action, None),
      user,
      authorizeScopeLimitDirective(usedLimitFuture, domain, action, user))

  def authorizeAuthResultAsync[T](
      authFut: Future[AuthResult[T]]
  ): Directive0 = {
    SecurityDirectives.authorizeAsync { authFut map { _.toBoolean } }
  }

  def circeDecodingError = ExceptionHandler {
    case df: DecodingFailure =>
      complete {
        (StatusCodes.BadRequest, DecodingFailure.showDecodingFailure.show(df))
      }
  }
}
