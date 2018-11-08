package com.rasterfoundry.backsplash.parameters

import com.rasterfoundry.backsplash.nodes.ProjectNode

import org.http4s._

import scala.util.Try

import java.util.UUID

object Parameters {

  implicit val uuidQueryParamDecoder: QueryParamDecoder[UUID] =
    QueryParamDecoder[String].map(UUID.fromString)

  object UUIDWrapper {
    def unapply(s: String): Option[UUID] = {
      if (!s.isEmpty) Try(UUID.fromString(s)).toOption else None
    }
  }
}
