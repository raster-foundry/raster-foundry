import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M139 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(139)(
    List(
      sqlu"""
UPDATE images
SET sourceuri = replace(sourceuri, '%7C', '|')
WHERE scene IN (
  SELECT id FROM scenes WHERE
  datasource IN ('a11b768b-d869-476e-a1ed-0ac3205ed761', '55735945-9da5-47c3-8ae4-572b5e11205b')
);
"""
    ))
}
