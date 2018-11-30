import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M35 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(35)(
    List(
      sqlu"""
CREATE INDEX data_footprint_idx ON scenes USING GIST (data_footprint);
CREATE INDEX created_at_idx ON scenes (created_at);
CREATE INDEX acquisition_date_idx ON scenes (acquisition_date);
CREATE INDEX datasource_idx ON scenes (datasource);
CREATE INDEX visibility_idx ON scenes (visibility);
"""
    ))
}
