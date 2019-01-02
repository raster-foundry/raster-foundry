package com.rasterfoundry.backsplash

import org.http4s.server.middleware.Metrics
import org.http4s.metrics.dropwizard.Dropwizard
import com.codahale.metrics.{Timer => MetricsTimer, _}
import com.codahale.metrics.graphite.{Graphite, GraphiteReporter}
import cats.effect.{IO, Clock}

import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit
import java.util.Locale
import java.io.File

class MetricsRegistrator(implicit clock: Clock[IO]) {
  val registry = new MetricRegistry()

  val middleware = Metrics[IO](Dropwizard[IO](registry, "server")) _

  def newTimer(clazz: Class[_], label: String): MetricsTimer =
    registry.timer(MetricRegistry.name(clazz.getTypeName, label))

  def timedIO[A](io: IO[A], t: MetricsTimer) =
    for {
      time <- IO(t.time())
      theIO <- io
      _ <- IO(time.stop())
    } yield theIO

  def timedIO[A](io: IO[A], clazz: Class[_], label: String): IO[A] = {
    val timer = newTimer(clazz, label)
    timedIO[A](io, timer)
  }

  def reportToConsole = {
    val reporter = ConsoleReporter
      .forRegistry(registry)
      .convertRatesTo(TimeUnit.SECONDS)
      .convertDurationsTo(TimeUnit.MILLISECONDS)
      .build()
    reporter.start(1, TimeUnit.SECONDS)
  }

  def reportToCSV(f: File) = {
    val reporter = CsvReporter
      .forRegistry(registry)
      .formatFor(Locale.US)
      .convertRatesTo(TimeUnit.SECONDS)
      .convertDurationsTo(TimeUnit.MILLISECONDS)
      .build(f)
    reporter.start(1, TimeUnit.SECONDS)
  }

  def reportToGraphite(graphiteUrl: String) = {
    val addr =
      new InetSocketAddress(graphiteUrl, 2003)
    val graphite = new Graphite(addr)
    val reporter = GraphiteReporter
      .forRegistry(registry)
      .convertRatesTo(TimeUnit.SECONDS)
      .convertDurationsTo(TimeUnit.MILLISECONDS)
      .build(graphite)
    reporter.start(1, TimeUnit.SECONDS)
  }
}
