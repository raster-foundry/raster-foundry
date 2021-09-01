package com.rasterfoundry

import com.rasterfoundry.database.filter.Filterables
import com.rasterfoundry.database.meta.RFMeta
import com.rasterfoundry.datamodel.Credential
import com.rasterfoundry.datamodel.ExportAssetType

import cats.data.NonEmptyList
import doobie.Get
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

    implicit def nelExportAssetTypesGet(implicit ev: Get[List[String]]) =
      ev.map { (list: List[String]) =>
        NonEmptyList.fromListUnsafe(list.map {
          ExportAssetType.unsafeFromString(_)
        })
      }

    implicit def nelExportAssetTypesPut(implicit ev: Put[List[String]]) =
      ev.contramap { (nel: NonEmptyList[ExportAssetType]) =>
        nel.toList.map { _.toString }
      }

  }
}
