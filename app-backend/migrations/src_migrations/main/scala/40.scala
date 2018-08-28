import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M40 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(40)(
    List(
      sqlu"""
  CREATE TYPE ingest_status AS ENUM ('NOTINGESTED', 'TOBEINGESTED', 'INGESTING', 'INGESTED', 'FAILED');

  ALTER TABLE scenes ADD COLUMN ingest_status ingest_status NOT NULL DEFAULT 'NOTINGESTED';
"""
    ))
}
