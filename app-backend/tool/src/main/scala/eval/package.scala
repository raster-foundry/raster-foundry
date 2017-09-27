package com.azavea.rf.tool

import cats._
import cats.data._
import cats.data.Validated._
import cats.implicits._


package object eval {
  /** The Interpreted type is either a list of failures or a compiled MapAlgebra operation */
  type Interpreted[A] = ValidatedNel[InterpreterError, A]
}
