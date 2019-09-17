package com.rasterfoundry.http4s.xray

import com.rasterfoundry.http4s.Config

final case class Segment[F[_]](name: String,
                               id: String,
                               trace_id: String,
                               start_time: Double,
                               end_time: Option[Double],
                               in_progress: Option[Boolean],
                               parent_id: Option[String],
                               annotations: Map[String, String],
                               _type: Option[String],
                               http: Option[XrayHttp]) {
  val aws: Map[String, collection.Map[String, String]] = Map(
    "ec2" -> Config.ec2.ec2Data,
    "ecs" -> Config.ecs.ecsInstance
  )
}
