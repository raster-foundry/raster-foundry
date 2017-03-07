package com.azavea.rf.api

import com.azavea.rf.datamodel._

import io.circe._
import cats.syntax.either._

import java.sql.Timestamp
import java.time.Instant

package object tool extends RfJsonProtocols {
  //implicit val paginatedToolFormat = jsonFormat6(PaginatedResponse[Tool.WithRelated])
  /** Circe rules */
  implicit def encodePaginated[A: Encoder] =
    Encoder.forProduct6("count", "hasPrevious", "hasNext", "page", "pageSize", "results")({pr: PaginatedResponse[A] =>
      (pr.count, pr.hasPrevious, pr.hasNext, pr.page, pr.pageSize, pr.results)
    })


  implicit val timestampEncoder: Encoder[Timestamp] =
    Encoder.encodeString.contramap[Timestamp](_.toInstant.toString)
  implicit val timestampDecoder: Decoder[Timestamp] =
    Decoder.decodeString.emap { str =>
      Either.catchNonFatal(Timestamp.from(Instant.parse(str))).leftMap(t => "Timestamp")
    }

}
