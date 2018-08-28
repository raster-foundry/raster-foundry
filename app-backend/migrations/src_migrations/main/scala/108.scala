import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M108 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(108)(
    List(
      sqlu"""
CREATE TYPE scene_type AS ENUM ('AVRO', 'COG');
ALTER TABLE scenes ADD COLUMN scene_type scene_type DEFAULT 'AVRO';
"""
    ))
}
