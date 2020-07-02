package com.rasterfoundry

import com.rasterfoundry.database.filter.Filterables
import com.rasterfoundry.database.meta.RFMeta
import com.rasterfoundry.datamodel.Credential

import doobie.Meta
import io.estatico.newtype.macros.newtype

import java.util.UUID

package object database {

  // to differentiate copy operations between parent and child annotation projects
  @newtype case class ParentAnnotationProjectId(parentAnnotationProjectId: UUID)
  @newtype case class ChildAnnotationProjectId(childAnnotationProjectId: UUID)

  object Implicits extends RFMeta with Filterables {
    def fromString(s: String): Credential = {
      Credential.apply(Some(s))
    }

    def toString(c: Credential): String = {
      c.token match {
        case Some(s) => s
        case _       => ""
      }
    }

    implicit val credMeta2: Meta[Credential] =
      Meta[String].timap(fromString)(toString)

  }
}
