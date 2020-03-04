CREATE OR REPLACE FUNCTION public.ST_MakeGrid (
  bound_polygon public.geometry,
  width_step double precision,
  height_step double precision
)
RETURNS public.geometry AS
$body$
DECLARE
  Xmin DOUBLE PRECISION;
  Xmax DOUBLE PRECISION;
  Ymax DOUBLE PRECISION;
  X DOUBLE PRECISION;
  Y DOUBLE PRECISION;
  NextX DOUBLE PRECISION;
  NextY DOUBLE PRECISION;
  CPoint public.geometry;
  sectors public.geometry[];
  newSector public.geometry;
  i INTEGER;
  SRID INTEGER;
  source_srid INTEGER;
BEGIN
  source_srid := ST_SRID(bound_polygon);
  bound_polygon := ST_Transform(bound_polygon, 4326);
  Xmin := ST_XMin(bound_polygon);
  Xmax := ST_XMax(bound_polygon);
  Ymax := ST_YMax(bound_polygon);
  SRID := 4326;
  Y := ST_YMin(bound_polygon); --current sector's corner coordinate
  i := -1;
  <<yloop>>
  LOOP
    IF (Y > Ymax) THEN
        EXIT;
    END IF;
    X := Xmin;
    <<xloop>>
    LOOP
      IF (X > Xmax) THEN
          EXIT;
      END IF;
      CPoint := ST_SetSRID(ST_MakePoint(X, Y), SRID);
      NextX := ST_X(ST_Project(CPoint, $2, radians(90))::geometry);
      NextY := ST_Y(ST_Project(CPoint, $3, radians(0))::geometry);
      i := i + 1;
      newSector := ST_MakeEnvelope(X, Y, NextX, NextY, SRID);
      IF (ST_Intersects(newSector, bound_polygon)) THEN
        sectors[i] := ST_Transform(ST_Intersection(newSector, bound_polygon), source_srid);
      END IF;
      X := NextX;
    END LOOP xloop;
    CPoint := ST_SetSRID(ST_MakePoint(X, Y), SRID);
    NextY := ST_Y(ST_Project(CPoint, $3, radians(0))::geometry);
    Y := NextY;
  END LOOP yloop;

  RETURN ST_Collect(sectors);
END;
$body$
LANGUAGE 'plpgsql';
