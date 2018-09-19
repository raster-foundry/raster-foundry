import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M150 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(150)(
    List(
      sqlu"""
ALTER TABLE scenes ALTER COLUMN ingest_status TYPE varchar(255);
ALTER TABLE scenes ALTER COLUMN ingest_status DROP DEFAULT;
DROP TYPE ingest_status;
CREATE TYPE ingest_status AS ENUM ('NOTINGESTED', 'QUEUED', 'TOBEINGESTED', 'INGESTING', 'INGESTED', 'FAILED');
ALTER TABLE scenes ALTER COLUMN ingest_status TYPE ingest_status USING ingest_status::ingest_status;
"""
    ))
}
