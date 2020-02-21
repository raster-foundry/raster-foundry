package com.rasterfoundry.common

import net.spy.memcached.{ClientMode, DefaultConnectionFactory, FailureMode}

import java.util.concurrent.ExecutorService

class BacksplashConnectionFactory(executorService: ExecutorService)
    extends DefaultConnectionFactory() {
  override def getClientMode: ClientMode = Config.memcached.clientMode

  override def getOperationTimeout: Long =
    Config.memcached.timeout

  override def getFailureMode: FailureMode = FailureMode.Cancel

  override def getListenerExecutorService: ExecutorService = executorService
}
