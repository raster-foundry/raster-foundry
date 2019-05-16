package com.rasterfoundry.backsplash

final case class OverviewConfig(location: Option[String], minZoom: Option[Int])

object OverviewConfig {
  def empty: OverviewConfig = OverviewConfig(None, None)
}
