package com.rasterfoundry.backsplash.server

import cats.MonoidK
import cats.data.OptionT
import cats.effect.Sync
import cats.implicits._
import org.http4s._
import scalacache._
import scalacache.caffeine._
import scalacache.modes.sync._

/** Shut down this instance of the tile server after n requests */
object QuotaMiddleware {
  def apply[F[_]: Sync](routes: HttpRoutes[F], cache: CaffeineCache[Int])(
      implicit F: MonoidK[OptionT[F, ?]]
  ) = HttpRoutes { req: Request[F] =>
    {
      val served = cache.get("requestsServed") getOrElse { 0 }
      val incremented = served + 1
      println(s"Requests served: $incremented")
      cache.put("requestsServed")(incremented, None)
      F.empty[Response[F]]
    } <+> routes.run(req)
  }
}
