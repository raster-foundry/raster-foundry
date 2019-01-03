package rfgatling

import com.typesafe.config.ConfigFactory

object Config {
  private val config = ConfigFactory.load()

  object RF {
    val apiHost = config.getString("gatling.rf.apiHost")
    val tileHost = config.getString("gatling.rf.tileHost")

    val tokenEndpoint = apiHost + config.getString("gatling.rf.tokenRoute")
    val projectEndpoint = apiHost + config.getString("gatling.rf.projectRoute")

    val projectIds = config.getString("gatling.rf.projectIds")
    val refreshToken = config.getString("gatling.rf.refreshToken")
  }

  object TMS {
    val minZoom = config.getInt("gatling.tms.minZoom")
    val maxZoom = config.getInt("gatling.tms.maxZoom")
    val randomSeed = config.getInt("gatling.tms.randomSeed")
  }

  object Users {
    val count = config.getInt("gatling.users.count")
    val rampupTimeSeconds = config.getInt("gatling.load.rampupTime")
  }

  object Load {
    val durationMinutes = config.getInt("gatling.load.durationMinutes")
  }
}
