import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M43 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(43)(
    List(
      sqlu"""
ALTER TABLE tools ADD COLUMN definition JSONB NOT NULL DEFAULT '{}';

UPDATE tools
SET definition = '{
    "apply": "-",
    "tag": "final",
    "label": "Vegetation Change",
    "args": [
        {
            "label": "Detect Vegetation - Before",
            "tag": "class0",
            "apply": "reclassify",
            "args": {
                "breaks": {
                    "type": "class-breaks"
                },
                "layer": {
                    "label": "Vegetation Index - Before",
                    "tag": "ndvi0",
                    "apply": "ndvi",
                    "args": [{
                        "type": "layer",
                        "label": "Area of Interest - Before",
                        "tag": "input_0"
                    }]
                }
            }
        },
        {
            "apply": "reclassify",
            "label": "Detect Vegetation - After",
            "tag": "class1",
            "args": {
                "breaks": {
                    "type": "class-breaks"
                },
                "layer": {
                    "label": "Vegetation Index - After",
                    "tag": "ndvi1",
                    "apply": "ndvi",
                    "args": [{
                        "type": "layer",
                        "label": "Area of Interest - After",
                        "tag": "input_1"
                    }]
                }
            }
        }
    ]
}'

WHERE id = 'b237a825-203e-44bc-9cfa-81cff9f28641';
    """
    ))
}
