package com.rasterfoundry.datamodel

import io.circe.{Decoder, Encoder}

object newtypes {

  // these aren't actually newtypes, because newtypes don't have typetags,
  // which makes them not cooperate especially well with doobie
  // we'll pay some runtime overhead and boilerplate for more convenient
  // doobie work in CirceJsonbMeta.

  // however, they're spiritually similar to newtypes, since they're just
  // value classes with encoders / decoders, so I'm keeping the object name

  class AsyncJobErrors(val value: List[String]) extends AnyVal

  object AsyncJobErrors {

    implicit val encAsyncJobErrors: Encoder[AsyncJobErrors] =
      Encoder[List[String]].contramap(_.value)
    implicit val decAsyncJobErrors: Decoder[AsyncJobErrors] =
      Decoder[List[String]].map(new AsyncJobErrors(_))

  }

  class CreatedUserIds(val value: List[String]) extends AnyVal

  object CreatedUserIds {
    implicit val encAsyncJobErrors: Encoder[CreatedUserIds] =
      Encoder[List[String]].contramap(_.value)
    implicit val decAsyncJobErrors: Decoder[CreatedUserIds] =
      Decoder[List[String]].map(new CreatedUserIds(_))
  }
}
