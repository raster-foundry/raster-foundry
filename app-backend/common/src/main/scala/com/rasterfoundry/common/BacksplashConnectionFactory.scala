package com.rasterfoundry.common

import java.util.concurrent.ExecutorService

import net.spy.memcached.{ClientMode, DefaultConnectionFactory, FailureMode}

class BacksplashConnectionFactory(executorService: ExecutorService)
    extends DefaultConnectionFactory() {
  override def getClientMode: ClientMode = Config.memcached.clientMode

  override def getOperationTimeout: Long =
    Config.memcached.timeout

  override def getFailureMode: FailureMode = FailureMode.Cancel

  override def getListenerExecutorService: ExecutorService = executorService
}
