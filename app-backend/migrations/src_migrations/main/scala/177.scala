import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M177 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(177)(
    List(
      sqlu"""
-- Fix scenes on the prime meridian that were split by a faulty lambda script

UPDATE scenes
   SET data_footprint = (
     SELECT
       ST_TRANSFORM(
         ST_SHIFTLONGITUDE(
           ST_MULTI(
             ST_REMOVEREPEATEDPOINTS(
               ST_MAKEPOLYGON(
                 ST_ADDPOINT(geom.line, st_startpoint(geom.line))
               )
             )
           )
         ),
         3857
       )
       from (
         select
           ST_MAKELINE(
             Array(
               SELECT pts.geom
                 FROM
                     ST_DUMPPOINTS(
                       ST_UNARYUNION(
                         ST_SHIFTLONGITUDE(
                           ST_TRANSFORM(scenes.data_footprint, 4326))))
                     AS pts
                WHERE NOT ST_INTERSECTS(
                  pts.geom,
                  ST_SETSRID(
                    ST_MAKELINE(
                      ST_MAKEPOINT(180,90),
                      ST_MAKEPOINT(180, -90)
                    ),
                    4326)
                )
             )
           ) line
       ) geom
   )
       FROM
       ST_TRANSFORM('SRID=4326;POLYGON((-180 -78, -180 78, -170 78, -170 -78, -180 -78))'::geometry, 3857) AS negative_antimeridian,
       ST_TRANSFORM('SRID=4326;POLYGON((-5 -78, -5 78, -10 78, -10 -78, -5 -78))'::geometry, 3857) AS offset_prime_antimeridian,
       ST_TRANSFORM(ST_SetSRID(ST_MAKELINE(ST_MAKEPOINT(0, -89), ST_MAKEPOINT(0,89)), 4326), 3857) AS primemeridian
 WHERE
  (
    ST_INTERSECTS(scenes.data_footprint, negative_antimeridian) AND
    ST_INTERSECTS(scenes.data_footprint, offset_prime_antimeridian) AND
    NOT ST_INTERSECTS(scenes.data_footprint, primemeridian)
  ) and
  scenes.datasource IN (
    '697a0b91-b7a8-446e-842c-97cda155554d', '4a50cb75-815d-4fe5-8bc1-144729ce5b42',
    '55735945-9da5-47c3-8ae4-572b5e11205b', 'eb34ce6d-acf5-49a3-ae43-5b4480a3ff7a'
  );
"""
    ))
}
