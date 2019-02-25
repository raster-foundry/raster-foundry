import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M168 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(168)(
    List(
      sqlu"""
ALTER TABLE licenses ADD CONSTRAINT id_is_unique UNIQUE (id);
ALTER TABLE tools DROP COLUMN license;
ALTER TABLE tools
  ADD COLUMN license integer references licenses(id),
  ADD COLUMN single_source boolean NOT NULL DEFAULT false;
"""
    ))
}
