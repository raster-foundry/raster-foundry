package com.azavea.rf.utils


/** Standardizes strings, removes spaces, etc converts to lowercase */
object Slug {
  def apply(input:String) = slugify(input)

  def slugify(input: String): String = {
    import java.text.Normalizer
    Normalizer.normalize(input, Normalizer.Form.NFD)
      .replaceAll("[^\\w\\s-]", ""
      .replace('-', ' ')
      .trim
      .replaceAll("\\s+", "-")
      .toLowerCase)
  }
}
