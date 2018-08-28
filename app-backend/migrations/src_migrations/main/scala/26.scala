import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M26 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(26)(
    List(
      sqlu"""
       CREATE TABLE model_categories (
         id UUID PRIMARY KEY NOT NULL,
         created_at TIMESTAMP NOT NULL,
         modified_at TIMESTAMP NOT NULL,
         created_by VARCHAR(255) REFERENCES users(id) NOT NULL,
         modified_by VARCHAR(255) REFERENCES users(id) NOT NULL,
         category VARCHAR(255) NOT NULL
       );

       ALTER TABLE
         model_categories
       ADD CONSTRAINT
         model_categories_unique_constraint
       UNIQUE (category);
"""
    ))
}
