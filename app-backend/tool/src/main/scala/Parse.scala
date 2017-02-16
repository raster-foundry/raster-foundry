package com.azavea.rf.tool

import spray.json._
import geotrellis.raster.op._
import shapeless._

object Defines {
  // Functions available in curren `include` scope
  type Scope = Map[Symbol, Op]
  type FunctionFormat = JsValue => Op
}

class OpParser(scope: Map[Symbol, Op]) {
  // TODO: make Division
  // TODO: make Mask

  def division(args: JsValue) = args match {
    case obj: JsObject => sys.error("not supported?")
    case arr: JsArray =>
      // TODO: is `name` actually a valid symbol ?
      // TODO: does arr have at least two members ?
      // Should this constructo be lefted to Op ?
      ???
    case _ =>
      throw new DeserializationException("bad arg format")
  }
}

object OpRegistry {
  // parsing dispatch based on op name from the "apply" field
  def apply(name: String): Option[RootJsonReader[Op]] = ???
}

/** This class defines how to turn a list of objects into an Op of specific kind */
abstract class PositionalArgs(val posFormats: JsonReader[_]*) {
  // we have to cast everything until we have HLists
  def fromPositional(args: Array[_]): Op
}

/** Defines how to turn a map of valuees into an Op of a specific kind */
abstract class NamedArgs(val namedFormats: Map[String, JsonReader[_]]) {

}

class DivParser extends RootJsonReader[Op] {

  def read(json: JsValue): Op = {
    json match {
      case obj: JsObject =>
        ??? // named argument list
        // get HMap of JsReader for shit, use it to parse, delicate
      case arr: JsArray =>
        ??? // positional argument list
        // get HList of JsReader for shit, use it to parse, deligate
      case _ =>
        throw new DeserializationException(s"Expected argument list, found: $json")
    }
  }
}

class OpReader extends RootJsonReader[Op] {
  def read(json: JsValue): Op = {
    json match {
      // layer identifier
      case JsString(identifier) =>
        Op.Var(identifier)

      // op application try to look it up in the registry
      case obj: JsObject if obj.fields.contains("apply") && obj.fields.contains("args") =>
        val opName = obj.fields("apply").asInstanceOf[JsString].value
        // TODO: handle lookup failure better better
        // TODO: without scope this can not parse references to ops defined in "include" section
        OpRegistry(opName).get.read(obj.fields("args"))
      case _ =>
        throw new DeserializationException(s"Expected either layer identifier of op apply, found: $json")
    }
  }
}

object OpParser {

  type SingleOpParser = JsValue => Op
  implicit object ApplyFormat extends RootJsonFormat[Op] {
    def read(json: JsValue): Op = {
      json.asJsObject.getFields("apply", "args") match {
        case Seq(function, args) =>
          // dispatch to function parser, give it the args

          // args can be either list of named
          // if list map JsValue => Op
          ???
      }
    }

    def write(op: Op): JsValue = {
      ???
    }
  }

  def parse(blog: String): Op = ???
}
