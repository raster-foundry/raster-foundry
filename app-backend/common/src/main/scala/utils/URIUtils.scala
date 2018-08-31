package com.azavea.rf.common.utils

import java.net.URI

object URIUtils {
  @SuppressWarnings(Array("NullParameter"))
  def withNoParams(uri: URI): URI =
    new URI(
      uri.getScheme,
      uri.getAuthority,
      uri.getPath,
      null, // Ignore the query part of the input url
      uri.getFragment
    )

  def withNoParams(str: String): String =
    withNoParams(new URI(str)).toString

}
