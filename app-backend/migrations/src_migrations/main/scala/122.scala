import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M122 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(122)(
    List(
      sqlu"""
CREATE INDEX IF NOT EXISTS act_typ_idx ON access_control_rules(action_type);
CREATE INDEX IF NOT EXISTS obj_act_idx ON access_control_rules(object_type, action_type);
CREATE INDEX IF NOT EXISTS obj_id_idx ON access_control_rules(object_id);
CREATE INDEX IF NOT EXISTS obj_typ_idx ON access_control_rules(object_type);
CREATE INDEX IF NOT EXISTS sub_id_idx ON access_control_rules(subject_id);
CREATE INDEX IF NOT EXISTS sub_typ_idx ON access_control_rules(subject_type);
"""
    )
  )
}
