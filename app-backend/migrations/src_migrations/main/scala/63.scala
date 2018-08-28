import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M63 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(63)(
    List(
// Note:
// The -> operator extracts the JSON value at a given key
// The ->> operator extracts the JSON value at a given key *as a string*
// These operators' behavior when combined with type-casting is a bit counterintuitive.
// '{"a": null}'::jsonb->'a'::TEXT::BOOLEAN will complain that 'null' (the string 'null')
// cannot be cast to boolean.
// However,
// '{"a": null}'::jsonb->>'a'::TEXT::BOOLEAN will properly convert to an empty string and from
// there to FALSE.
      sqlu"""
UPDATE scenes_to_projects SET mosaic_definition = json_build_object(
    'redBand', mosaic_definition->'redBand',
    'greenBand', mosaic_definition->'greenBand',
    'blueBand', mosaic_definition->'blueBand',

    'gamma', json_build_object(
        'enabled', (
            mosaic_definition->>'redGamma' IS NOT NULL AND
            mosaic_definition->>'greenGamma' IS NOT NULL AND
            mosaic_definition->>'blueGamma' IS NOT NULL
        ),
        'redGamma', mosaic_definition->'redGamma',
        'greenGamma', mosaic_definition->'greenGamma',
        'blueGamma', mosaic_definition->'blueGamma'
    ),

    'bandClipping', json_build_object(
        'enabled', (
            mosaic_definition->>'redMin' IS NOT NULL AND
            mosaic_definition->>'redMax' IS NOT NULL AND
            mosaic_definition->>'greenMin' IS NOT NULL AND
            mosaic_definition->>'greenMax' IS NOT NULL AND
            mosaic_definition->>'blueMin' IS NOT NULL AND
            mosaic_definition->>'blueMax' IS NOT NULL
        ),
        'redMin', mosaic_definition->'redMin',
        'redMax', mosaic_definition->'redMax',
        'greenMin', mosaic_definition->'greenMin',
        'greenMax', mosaic_definition->'greenMax',
        'blueMin', mosaic_definition->'blueMin',
        'blueMax', mosaic_definition->'blueMax'
    ),

    'tileClipping', json_build_object(
        'enabled', (
            mosaic_definition->>'min' IS NOT NULL AND
            mosaic_definition->>'max' IS NOT NULL
        ),
        'min', mosaic_definition->'min',
        'max', mosaic_definition->'max'
    ),

    'sigmoidalContrast', json_build_object(
        'enabled', (
            mosaic_definition->>'alpha' IS NOT NULL AND
            mosaic_definition->>'beta' IS NOT NULL
        ),
        'alpha', mosaic_definition->'alpha',
        'beta', mosaic_definition->'beta'
    ),

    'saturation', json_build_object(
        'enabled', mosaic_definition->>'saturation' IS NOT NULL,
        'saturation', mosaic_definition->'saturation'
    ),

    'equalize', json_build_object(
        'enabled', mosaic_definition->>'equalize' IS NOT NULL AND (mosaic_definition->>'equalize')::TEXT::BOOLEAN
    ),

    'autoBalance', json_build_object(
        'enabled', mosaic_definition->>'autoBalance' IS NOT NULL AND (mosaic_definition->>'autoBalance')::TEXT::BOOLEAN
    )
)
    """
    ))
}
