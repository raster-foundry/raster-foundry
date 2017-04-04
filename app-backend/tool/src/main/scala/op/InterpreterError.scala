package com.azavea.rf.tool.op

import java.net.URI
import java.util.UUID


sealed trait InterpreterError { val id: UUID }
case class MissingParameter(id: UUID) extends InterpreterError
case class TileRetrievalError(id: UUID, attempted: URI) extends InterpreterError

