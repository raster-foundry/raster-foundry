package com.rasterfoundry.batch.groundwork

import types._

trait IntercomNotifier[F[_]] {
  def notifyUser(
      intercomToken: IntercomToken,
      userId: UserId,
      msg: Message
  ): F[Unit]
  def userByExternalId(
      intercomToken: IntercomToken,
      externalId: ExternalId
  ): F[IntercomSearchResponse]
}

class LiveIntercomNotifier[F[_]] extends IntercomNotifier[F] {
  def notifyUser(
      intercomToken: IntercomToken,
      userId: UserId,
      msg: Message
  ): F[Unit] = ???
  def userByExternalId(
      intercomToken: IntercomToken,
      externalId: ExternalId
  ): F[IntercomSearchResponse] = ???
}
