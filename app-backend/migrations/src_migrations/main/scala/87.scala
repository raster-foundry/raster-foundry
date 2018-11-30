import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M87 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(87)(
    List(
      sqlu"""
delete from scenes
where id in (
  select id from (
    select id, owner, datasource,  ROW_NUMBER() over (
      partition by name order by created_at
    ) as rnum
    from scenes
    where datasource = '697a0b91-b7a8-446e-842c-97cda155554d'
    and owner = 'rf|airflow-user'
  ) t
  where t.rnum > 1
  and id not in (
    select scenes.id
    from scenes join scenes_to_projects on scenes.id = scenes_to_projects.scene_id
  )
);
"""
    ))
}
