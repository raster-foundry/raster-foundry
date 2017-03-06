package com.azavea.rf.tool

import io.circe._
import io.circe.optics.JsonPath._

import scala.util.Try
import java.util.UUID
import java.security.InvalidParameterException

package object ast {

  implicit class CirceMapAlgebraJsonMethods(val self: Json) {
    def _id: Option[UUID] = root.id.string.getOption(self).map(UUID.fromString(_))
    def _type: Option[String] = root.`type`.string.getOption(self)
    def _label: Option[String] = root.label.string.getOption(self)
    def _symbol: Option[String] = root.selectDynamic("apply").string.getOption(self)

    def _fields: Option[Seq[String]] = root.obj.getOption(self).map(_.fields)
  }

  implicit class CirceMapAlgebraHCursorMethods(val self: HCursor) {
    def _id: Option[UUID] = self.value._id
    def _type: Option[String] = self.value._type
    def _label: Option[String] = self.value._label
    def _symbol: Option[String] = self.value._symbol

    def _fields: Option[Seq[String]] = self.value._fields
  }

}
