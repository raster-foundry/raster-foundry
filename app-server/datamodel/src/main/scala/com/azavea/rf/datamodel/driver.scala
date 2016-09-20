package com.azavea.rf.datamodel.driver

import com.azavea.rf.datamodel.enums._
import com.github.tminglei.slickpg._
import spray.json._
import scala.collection.immutable.Map


// Custom json protocol that supports Map[String, Any] for JSONB
trait RFDatabaseJsonProtocol extends DefaultJsonProtocol {

  implicit object AnyJsonFormat extends JsonFormat[Any] {
    def write(x: Any) = x match {
      case n: Int => JsNumber(n)
      case s: String => JsString(s)
      case x: Seq[_] => seqFormat[Any].write(x)
      case m: Map[String, _] => mapFormat[String, Any].write(m)
      case b: Boolean if b == true => JsTrue
      case b: Boolean if b == false => JsFalse
      case x => serializationError("Do not understand object of type " + x.getClass.getName)
    }
    def read(value: JsValue) = value match {
      case JsNumber(n) => n.intValue()
      case JsString(s) => s
      case a: JsArray => listFormat[Any].read(value)
      case o: JsObject => mapFormat[String, Any].read(value)
      case JsTrue => true
      case JsFalse => false
      case x => deserializationError("Do not understand how to deserialize " + x)
    }
  }
}


/** Custom Postgres driver that adds custom column types and implicit conversions
  *
  * Adds support for the following column types
  *  - JSONB
  *  - JobStatus (enum)
  *  - Visibility (enum)
  *  - text[] (array text)
  */
trait ExtendedPostgresDriver extends ExPostgresDriver
    with PgArraySupport
    with PgSprayJsonSupport
    with PgEnumSupport {

  override val pgjson = "jsonb"

  override val api = RFAPI

  // Implicit conversions to/from column types
  object RFAPI extends API
      with ArrayImplicits
      with JsonImplicits
      with RFDatabaseJsonProtocol {

    implicit val metadataMapper = MappedJdbcType.base[Map[String, Any], JsValue](_.toJson,
      _.convertTo[Map[String, Any]])
    implicit def strListTypeMapper = new SimpleArrayJdbcType[String]("text").to(_.toList)

    implicit val jobStatusTypeMapper = createEnumJdbcType[JobStatus]("JobStatus", _.repr,
      JobStatus.fromString, quoteName = false)
    implicit val jobStatusTypeListMapper = createEnumListJdbcType[JobStatus]("JobStatus",
      _.repr, JobStatus.fromString, quoteName = false)
    implicit val jobStatusColumnExtensionMethodsBuilder =
      createEnumColumnExtensionMethodsBuilder[JobStatus]
    implicit val jobStatusOptionColumnExtensionMethodsBuilder =
      createEnumOptionColumnExtensionMethodsBuilder[JobStatus]

    implicit val visibilityTypeMapper = createEnumJdbcType[Visibility]("Visibility", _.repr,
      Visibility.fromString, quoteName = false)
    implicit val visibilityTypeListMapper = createEnumListJdbcType[Visibility]("Visibility", _.repr,
      Visibility.fromString, quoteName = false)
    implicit val visibilityColumnExtensionMethodsBuilder =
      createEnumColumnExtensionMethodsBuilder[Visibility]
    implicit val visibilityOptionColumnExtensionMethodsBuilder =
      createEnumOptionColumnExtensionMethodsBuilder[Visibility]
  }
}

object ExtendedPostgresDriver extends ExtendedPostgresDriver
