package com.rasterfoundry.database

package object meta {
  trait RFMeta
      extends GtWktMeta
      with CirceJsonbMeta
      with EnumMeta
      with PermissionsMeta
}
