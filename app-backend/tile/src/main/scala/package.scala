package com.azavea.rf

import net.spy.memcached.internal.GetFuture

import scala.concurrent._
import scala.concurrent.duration._
import scala.util._
import scala.concurrent.ExecutionContext.Implicits.global
import java.util.concurrent.{Executors, TimeUnit}

package object tile {

  trait isFuture[A] {
    def asFuture[T]: Future[T]
  }

  implicit class memcachedFutureIsFuture[B <: AnyRef](fut: GetFuture[B]) extends isFuture[GetFuture[B]] {
    def asFuture[T]: Future[T] = {
      val promise = Promise[Object]()
      new Thread(new Runnable {
        def run() {
          promise.complete(Try{ fut.get })
        }
      }).start
      promise.future.map({ obj => obj.asInstanceOf[T] })
    }
  }

}
