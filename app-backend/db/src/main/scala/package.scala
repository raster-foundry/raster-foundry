package com.rasterfoundry

import com.rasterfoundry.database.filter.Filterables
import com.rasterfoundry.database.meta.RFMeta
import com.rasterfoundry.datamodel.Credential

import doobie.util.Meta

package object database {
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
