package com.azavea.rf.tile

import com.typesafe.config.ConfigFactory

object Config {
  private val config = ConfigFactory.load()

  object RF {
    val host = config.getString("gatling.rf.host")
    val tokenEndpoint = host + config.getString("gatling.rf.tokenRoute")
    val projectEndpoint = host + config.getString("gatling.rf.projectRoute")

    val projectId = config.getString("gatling.rf.projectId")
    val refreshToken = config.getString("gatling.rf.refreshToken")
  }

  object TMS {
    val template = config.getString("gatling.tms.template")
    val minZoom = config.getInt("gatling.tms.minZoom")
    val maxZoom = config.getInt("gatling.tms.maxZoom")
    val randomSeed = config.getInt("gatling.tms.randomSeed")
  }
  object Users {
    val count = config.getInt("gatling.users.count")
    val rampupTime = config.getInt("gatling.users.rampupTime")
  }
}
