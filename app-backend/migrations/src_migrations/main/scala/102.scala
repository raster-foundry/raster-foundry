import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M102 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(102)(
    List(
      sqlu"""
    UPDATE datasources
    SET name = 'Sentinel-2'
    WHERE id = '4a50cb75-815d-4fe5-8bc1-144729ce5b42';

    UPDATE scenes
    SET datasource = '4a50cb75-815d-4fe5-8bc1-144729ce5b42'
    WHERE datasource = 'c33db82d-afdb-43cb-a6ac-ba899e48638d';

    DELETE FROM datasources
    WHERE id = 'c33db82d-afdb-43cb-a6ac-ba899e48638d';
    """
    ))
}
