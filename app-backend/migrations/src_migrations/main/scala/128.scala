import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M128 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(128)(
    List(
      sqlu"""
UPDATE scenes
SET data_footprint = (
  SELECT ST_UNION(
    ST_COLLECT(ST_TRANSFORM(rightpoly.thegeom, 3857)),
    ST_COLLECT(ST_TRANSFORM(ST_TRANSLATE(leftpoly.thegeom, -360, 0), 3857))
  ) FROM
  (SELECT foo2.thegeom thegeom FROM
    (SELECT (ST_DUMP(foo.geom)).geom AS thegeom FROM
      (
        SELECT ST_SPLIT(ST_SHIFTLONGITUDE(ST_TRANSFORM(scenes.data_footprint, 4326)), splitline) AS geom
        FROM ST_SETSRID(ST_MAKELINE(ST_MAKEPOINT(180, -90),ST_MAKEPOINT(180, 90)), 4326) AS splitline
      ) AS foo
    ) AS foo2
    WHERE ST_XMAX(foo2.thegeom) > 180
  ) leftpoly,
  (SELECT foo3.thegeom thegeom FROM
    (SELECT (ST_DUMP(foo.geom)).geom AS thegeom FROM
      (
        SELECT ST_SPLIT(ST_SHIFTLONGITUDE(ST_TRANSFORM(scenes.data_footprint, 4326)), splitline) AS geom
        FROM ST_SETSRID(ST_MAKELINE(ST_MAKEPOINT(180, -90),ST_MAKEPOINT(180, 90)), 4326) AS splitline
      ) AS foo
    ) AS foo3
    WHERE ST_XMAX(foo3.thegeom) <= 180
  ) rightpoly
)
FROM
ST_TRANSFORM('SRID=4326;POLYGON((-180 -80, -180 80, -170 80, -170 -80, -180 -80))'::geometry, 3857) AS negative_antimeridian,
ST_TRANSFORM('SRID=4326;POLYGON((180 -80, 180 80, 170 80, 170 -80, 180 -80))'::geometry, 3857) AS positive_antimeridian,
ST_TRANSFORM(ST_SetSRID(ST_MAKELINE(ST_MAKEPOINT(0, -80), ST_MAKEPOINT(0,80)), 4326), 3857) AS primemeridian
WHERE
(ST_INTERSECTS(scenes.data_footprint, negative_antimeridian) OR
 ST_INTERSECTS(scenes.data_footprint, positive_antimeridian)) AND
 ST_INTERSECTS(scenes.data_footprint, primemeridian)
-- Landsat 8, Sentinel 2, MYD09A1, and ISERV (prod only)
AND scenes.datasource IN (
'697a0b91-b7a8-446e-842c-97cda155554d', '4a50cb75-815d-4fe5-8bc1-144729ce5b42',
 '55735945-9da5-47c3-8ae4-572b5e11205b', 'eb34ce6d-acf5-49a3-ae43-5b4480a3ff7a'
);
"""
    ))
}
