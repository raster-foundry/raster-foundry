import xerial.sbt.Sonatype._

publishMavenStyle := true

pgpPassphrase := Some(
  System.getenv().getOrDefault("PGP_PASSPHRASE", "").toCharArray())
pgpSecretRing := file("/root/.gnupg/secring.gpg")
usePgpKeyHex(System.getenv().getOrDefault("PGP_HEX_KEY", "0"))

sonatypeProfileName := "com.rasterfoundry"
sonatypeProjectHosting := Some(
  GitHubHosting(user = "raster-foundry",
                repository = "raster-foundry",
                email = "info@rasterfoundry.com"))
developers := List(
  Developer(id = "azavea",
            name = "Azavea Inc.",
            email = "systems@azavea.com",
            url = url("https://www.azavea.com"))
)
licenses := Seq(
  "Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.txt"))

// For Travis CI - see http://www.cakesolutions.net/teamblogs/publishing-artefacts-to-oss-sonatype-nexus-using-sbt-and-travis-ci
credentials ++= (for {
  username <- Option(System.getenv().get("SONATYPE_USERNAME"))
  password <- Option(System.getenv().get("SONATYPE_PASSWORD"))
} yield
  Credentials("Sonatype Nexus Repository Manager",
              "oss.sonatype.org",
              username,
              password)).toSeq
