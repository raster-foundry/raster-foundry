package com.rasterfoundry.database

import com.rasterfoundry.common.datamodel._
import com.rasterfoundry.database.filter._
import com.rasterfoundry.database.Implicits._

import doobie._
import doobie.postgres.implicits._
import doobie.implicits._

import java.util.UUID

object MetricDao extends Dao[Metric] {
  val tableName = "metrics"
  val selectF = sql"""SELECT
    id, period, metric_event, metric_value, requester
  FROM """ ++ tableF

  def unsafeGetMetricById(metricId: UUID): ConnectionIO[Metric] =
    query.filter(fr"id  = $metricId").select

  // See raster-foundry/raster-foundry #4914 for the beginnings of a plan for
  // querying and aggregating metrics

  def uniquenessFilters(metric: Metric) = {
    val periodStart = metric.period._1.toString ++ "T00:00:00Z"
    List(
      Some(fr"requester = ${metric.requester}"),
      Some(fr"""period @> $periodStart :: timestamp""")
    ) ++ Filters.metricQP(metric.metricEvent.toQueryParams)
  }

  def increment(metric: Metric): ConnectionIO[Int] = {
    println(s"Filters are ${uniquenessFilters(metric)}")
    val q = query
      .filter(uniquenessFilters(metric))
    q.exists flatMap {
      case false =>
        insert(metric)
      case true =>
        update(metric)
    }
  }

  def insert(metric: Metric): ConnectionIO[Int] = {
    val baseCount = 1
    val frag = (fr"""
      INSERT INTO metrics (id, period, metric_event, metric_value, requester)
      VALUES (
        ${metric.id}, ${metric.period}, ${metric.metricEvent}, $baseCount, ${metric.requester}
      );
      """)
    frag.update.run
  }

  def update(metric: Metric): ConnectionIO[Int] = {
    val frag = (Fragment.const(
      "UPDATE metrics SET metric_value = (metric_value + 1)") ++ Fragments
      .whereAndOpt(uniquenessFilters(metric): _*))
    frag.update.run
  }
}
