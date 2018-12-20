package com.rasterfoundry.backsplash

import geotrellis.server._
import geotrellis.vector._
import geotrellis.raster._
import geotrellis.raster.io.geotiff._
import cats.data.Validated._
import cats.effect.IO
import cats.implicits._
import org.scalatest._
import org.scalatest.prop.Checkers
import org.scalacheck.Prop.forAll
import com.azavea.maml.ast._

import BacksplashImageGen._

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import java.util.concurrent.Executors
import java.lang.management.ManagementFactory

class MetricsRegistratorSpec extends FunSuite with Checkers with Matchers {
  val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))
  implicit val cs = IO.contextShift(ec)
  implicit val t = IO.timer(ExecutionContext.global)

  test("wrapped IO should take ~2 seconds") {
    val mtr = new MetricsRegistrator()
    val work =
      mtr.timedIO(IO.sleep(2.seconds), classOf[MetricsRegistratorSpec], "2secs")
    work.unsafeRunSync
    val timer = mtr.registry
      .getTimers()
      .asScala("com.rasterfoundry.backsplash.MetricsRegistratorSpec.2secs")
    val snapshot = timer.getSnapshot().getValues().head
    assert(snapshot.nanoseconds.toSeconds == 2,
           "Expected 2 second sleep to take 2 seconds...")
  }

  test("internal, wrapped IO should take ~2 seconds") {
    val mtr = new MetricsRegistrator()
    val work = for {
      _ <- IO.sleep(2.seconds)
      _ <- mtr.timedIO(IO.sleep(2.seconds),
                       classOf[MetricsRegistratorSpec],
                       "2secs")
      _ <- IO.sleep(1.seconds)
    } yield ()
    work.unsafeRunSync
    val timer = mtr.registry
      .getTimers()
      .asScala("com.rasterfoundry.backsplash.MetricsRegistratorSpec.2secs")
    val snapshot = timer.getSnapshot().getValues().head
    assert(snapshot.nanoseconds.toSeconds == 2,
           "Expected 2 second sleep to take 2 seconds...")
  }
}
