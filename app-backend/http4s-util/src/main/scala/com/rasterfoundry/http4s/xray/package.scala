package com.rasterfoundry.http4s

import io.circe.Encoder

package object xray {

  implicit def segmentEncoder[A[_]]: Encoder[Segment[A]] =
    Encoder.forProduct11("name",
                         "id",
                         "trace_id",
                         "start_time",
                         "end_time",
                         "in_progress",
                         "parent_id",
                         "annotations",
                         "type",
                         "http",
                         "aws")(
      s =>
        (s.name,
         s.id,
         s.trace_id,
         s.start_time,
         s.end_time,
         s.in_progress,
         s.parent_id,
         s.annotations,
         s._type,
         s.http,
         s.aws))

}
