import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M170 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(170)(
    List(
      sqlu"DROP TABLE scenes_to_projects;"
    ))
}
