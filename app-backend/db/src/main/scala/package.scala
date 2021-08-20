package com.rasterfoundry

import com.rasterfoundry.database.filter.Filterables
import com.rasterfoundry.database.meta.RFMeta
import com.rasterfoundry.datamodel.Credential

import cats.data.NonEmptyList
import scala.reflect.runtime.universe.TypeTag

import doobie.Meta
import doobie.Put

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

    implicit def nelPut[A: TypeTag](implicit ev: Put[List[A]]) =
      ev.contramap { (nel: NonEmptyList[A]) =>
        nel.toList
      }

  }
}
