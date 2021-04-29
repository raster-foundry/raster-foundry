package com.rasterfoundry.batch.groundwork

import com.rasterfoundry.notification.intercom.Model._

import com.typesafe.config.ConfigFactory

object Config {

  val config = ConfigFactory.load()
  private val intercomConfig = config.getConfig("intercom")
  val intercomToken = IntercomToken(intercomConfig.getString("token"))
  val intercomAdminId = AdminId(intercomConfig.getString("adminId"))
  val groundworkUrlBase = intercomConfig.getString("groundworkUrlBase")
}
