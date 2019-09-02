package com.rasterfoundry.common

import net.spy.memcached.{ClientMode, DefaultConnectionFactory}

class BacksplashConnectionFactory extends DefaultConnectionFactory() {
  override def getClientMode: ClientMode = Config.memcached.clientMode

  override def getOperationTimeout: Long =
    Config.memcached.timeout
}
