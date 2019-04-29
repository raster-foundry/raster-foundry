package com.rasterfoundry.database

import com.rasterfoundry.common.datamodel._
import com.rasterfoundry.database.filter._
import com.rasterfoundry.database.Implicits._

import cats.implicits._
import doobie._
import doobie.postgres.implicits._
import doobie.implicits._

import java.time.LocalDate

object MetricDao extends Dao[Metric] {
  val tableName = "metrics"
  val selectF = sql"""
    SELECT metrics.id, period, metric_event, value, requester
  """

  def uniquenessFilters(metric: Metric) =
    List(
      Some(fr"requester = ${metric.requester}"),
      Some(fr"period @> '${metric.period._1}T00:00:00 :: timestamp'")
    ) ++ Filters.metricQP(metric.metricEvent.toQueryParams)

  def increment(metric: Metric): ConnectionIO[Int] = {
    query
      .filter(uniquenessFilters(metric))
      .selectOption flatMap {
      case None =>
        insert(metric)
      case Some(s) =>
        update(metric)
    }
  }

  def sumForRange(range: (LocalDate, LocalDate),
                  groupBy: String = "requester",
                  user: User,
                  params: MetricQueryParameters,
                  groupByF: Fragment): ConnectionIO[List[Metric]] =
    for {
      platform <- UserDao.unsafeGetUserPlatform(user.id)
      ugrsFiltered = Fragment.const(
        """
        (SELECT * FROM user_group_roles
         WHERE
           is_active=true and group_type='PLATFORM' and group_id='${platform.id}'
        ) filt_ugrs""")
      userScopedTableF = fr"metrics inner join " ++
        ugrsFiltered ++
        fr" ON filt_ugrs.user_id = metrics.requester"
      metrics <- List(selectF,
                      userScopedTableF,
                      Fragments.whereAndOpt(Filters.metricQP(params): _*),
                      groupByF)
        .intercalate(Fragment.empty)
        .query[Metric]
        .to[List]
    } yield metrics

  def insert(metric: Metric): ConnectionIO[Int] =
    (fr"""
      INSERT INTO metrics (id, period, metric_event, value, requester)
      VALUES (
        ${metric.id}, ${metric.period}, ${metric.metricEvent}, ${metric.value}, ${metric.requester}
      );
      """).query[Int].unique

  def update(metric: Metric): ConnectionIO[Int] =
    (fr"UPDATE metrics SET value = value + 1" ++ Fragments.whereAndOpt(
      uniquenessFilters(metric): _*)).query[Int].unique
}
