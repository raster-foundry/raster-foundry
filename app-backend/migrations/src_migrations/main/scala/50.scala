import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

/** We can't alter type inside a transaction block, and all migrations run inside
  * a transaction block, so instead we have to delete and recreate the type.
  */
object M50 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(50)(
    List(
      sqlu"""
ALTER TABLE uploads ALTER COLUMN upload_status TYPE varchar(255);
DROP TYPE upload_status;
CREATE TYPE upload_status AS ENUM ('CREATED', 'UPLOADING', 'UPLOADED', 'QUEUED', 'PROCESSING', 'COMPLETE', 'FAILED', 'ABORTED');
ALTER TABLE uploads ALTER COLUMN upload_status TYPE upload_status USING upload_status::upload_status;
"""
    ))
}
