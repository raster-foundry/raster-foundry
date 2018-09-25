package com.azavea.rf.batch

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.azavea.rf.batch.util.conf.Config
import com.azavea.rf.common.RollbarNotifier

import scala.concurrent.ExecutionContextExecutor

trait Job extends Config with RollbarNotifier {
  val name: String

  implicit lazy val system: ActorSystem = ActorSystem(s"$name-system")
  implicit lazy val materializer: ActorMaterializer = ActorMaterializer()
  implicit lazy val executionContext: ExecutionContextExecutor =
    materializer.executionContext

  /** ActorSystem needs to be closed manually. */
  def stop(): Unit = system.terminate()

  /** Run function should be defined for all Jobs */
  def run(): Unit
}
