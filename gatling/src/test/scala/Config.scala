package rfgatling

import com.typesafe.config.ConfigFactory

object Config {
  private val config = ConfigFactory.load()

  object RF {
    val apiHost = config.getString("gatling.rf.apiHost")
    val tokenEndpoint = apiHost + config.getString("gatling.rf.tokenRoute")
    val projectEndpoint = apiHost + config.getString("gatling.rf.projectRoute")

    val projectIds = config.getString("gatling.rf.projectIds")
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
