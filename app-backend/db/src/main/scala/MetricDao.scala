package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.database.filter._
import com.rasterfoundry.datamodel._

import doobie._
import doobie.implicits._

object MetricDao extends Dao[Metric] {
  val tableName = "metrics"
  val selectF = sql"""SELECT
    period, metric_event, requester, metric_value
  FROM """ ++ tableF

  // See raster-foundry/raster-foundry #4914 for the beginnings of a plan for
  // querying and aggregating metrics

  def getMetric(metric: Metric): ConnectionIO[Option[Metric]] =
    query.filter(uniquenessFilters(metric)).selectOption

  def unsafeGetMetric(metric: Metric): ConnectionIO[Metric] =
    query.filter(uniquenessFilters(metric)).select

  def uniquenessFilters(metric: Metric) = {
    val periodStart = metric.period._1.toString ++ "T00:00:00Z"
    List(
      Some(fr"metrics.requester = ${metric.requester}"),
      Some(fr"""metrics.period @> $periodStart :: timestamp""")
    ) ++ Filters.metricQP(metric.metricEvent.toQueryParams)
  }

  def insert(metric: Metric): ConnectionIO[Int] = {
    val baseCount = 1
    val frag = (fr"""
      INSERT INTO metrics (period, metric_event, metric_value, requester)
      VALUES (
        ${metric.period}, ${metric.metricEvent}, $baseCount, ${metric.requester}
      )
      ON CONFLICT ON CONSTRAINT metric_event_period_unique
      DO UPDATE
        SET metric_value = metrics.metric_value + 1
""" ++ Fragments.whereAndOpt(uniquenessFilters(metric): _*))
    frag.update.run
  }
}
