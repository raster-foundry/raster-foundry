import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M69 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(69)(
    List(
      sqlu"""

UPDATE tools set organization_id = 'dfac6307-b5ef-43f7-beda-b9f208bb7726';

UPDATE tools set definition = '{
    "type": "src",
    "id": "a1460c96-8377-4bf1-aad4-18a6e47093c6",
    "metadata": {
        "label": "Identity",
        "description": "The source for this input will be output directly"
    }
}' where title = 'Single Band Identity';

UPDATE tools set definition = '{
	"id": "875f9059-2b87-49d4-8d2a-3ecc47d4b14d",
	"metadata": {
		"label": "NDVI Difference"
	},
	"apply": "-",
	"args": [{
			"id": "a1460c96-8377-4bf1-aad4-18a6e47093c6",
			"args": [{
					"id": "fe1763d4-9b4d-4eba-9968-f7d707869d7f",
					"args": [{
              "type": "src",
							"id": "67013aad-21c5-4e7f-b8bd-5910e92aa425",
							"metadata": {
								"label": "NIR - Before"
							}
						},
						{
              "type": "src",
							"id": "a3c93582-a086-44e8-8bdf-0b2ab69f91d0",
							"metadata": {
								"label": "VIS - Before"
							}
						}
					],
					"apply": "-",
					"metadata": {
						"label": "NDVI - Subtraction"
					}
				},
				{
					"id": "b7315337-b4fc-45b7-951a-83f53703e76b",
					"args": [{
              "type": "src",
							"id": "67013aad-21c5-4e7f-b8bd-5910e92aa425",
							"metadata": {
								"label": "NIR - Before"
							}
						},
						{
              "type": "src",
							"id": "a3c93582-a086-44e8-8bdf-0b2ab69f91d0",
							"metadata": {
								"label": "VIS - Before"
							}
						}
					],
					"apply": "+",
					"metadata": {
						"label": "NDVI - Addition"
					}
				}
			],
			"apply": "/",
			"metadata": {
				"label": "NDVI - Division"
			}
		},
		{
			"id": "722519bc-3312-4c35-924b-9f1a155f83ec",
			"args": [{
					"id": "a98bb37c-5bea-4c19-864b-6ccb25442e5b",
					"args": [{
              "type": "src",
							"id": "f262f676-704b-494f-a75d-ae733236a2f3",
							"metadata": {
								"label": "NIR - After"
							}
						},
						{
              "type": "src",
							"id": "1db79bb1-0638-4950-8f9e-f5f4eacc7323",
							"metadata": {
								"label": "VIS - After"
							}
						}
					],
					"apply": "-",
					"metadata": {
						"label": "NDVI - Subtraction"
					}
				},
				{
					"id": "c71467fd-9cd3-4ace-89ce-7d758f71257e",
					"args": [{
              "type": "src",
							"id": "f262f676-704b-494f-a75d-ae733236a2f3",
							"metadata": {
								"label": "NIR - After"
							}
						},
						{
              "type": "src",
							"id": "1db79bb1-0638-4950-8f9e-f5f4eacc7323",
							"metadata": {
								"label": "VIS - After"
							}
						}
					],
					"apply": "+",
					"metadata": {
						"label": "NDVI - Addition"
					}
				}
			],
			"apply": "/",
			"metadata": {
				"label": "NDVI - Division"
			}
		}
	]
}' where title = 'NDVI Change Detection';


update tools set definition = '{
    "id": "a1460c96-8377-4bf1-aad4-18a6e47093c6",
    "args": [
      {
        "id": "fe1763d4-9b4d-4eba-9968-f7d707869d7f",
        "args": [
          {
            "type": "src",
            "id": "67013aad-21c5-4e7f-b8bd-5910e92aa425",
            "metadata": {
                "label": "NIR"
            }
          },
          {
            "type": "src",
            "id": "a3c93582-a086-44e8-8bdf-0b2ab69f91d0",
            "metadata": {
                "label": "VIS"
            }
          }
        ],
        "apply": "-",
        "metadata": {
            "label": "NDVI - Subtraction"
        }
      },
      {
        "id": "b7315337-b4fc-45b7-951a-83f53703e76b",
        "args": [
          {
            "type": "src",
            "id": "67013aad-21c5-4e7f-b8bd-5910e92aa425",
            "metadata": {
                "label": "NIR"
            }
          },
          {
            "type": "src",
            "id": "a3c93582-a086-44e8-8bdf-0b2ab69f91d0",
            "metadata": {
                "label": "VIS"
            }
          }
        ],
        "apply": "+",
        "metadata": {
            "label": "NDVI - Addition"
        }
      }
    ],
    "apply": "/",
    "metadata": {
        "label": "NDVI - Division"
    }
}' where title = 'Normalized Difference Vegetation Index - NDVI';

update tools set definition = '{
    "id": "fe1763d4-9b4d-4eba-9968-f7d707869d7f",
    "args": [
      {
        "type": "src",
        "id": "67013aad-21c5-4e7f-b8bd-5910e92aa425",
        "metadata": {
            "label": "A"
        }
      },
      {
        "type": "src",
        "id": "a3c93582-a086-44e8-8bdf-0b2ab69f91d0",
        "metadata": {
            "label": "B"
        }
      }
    ],
    "apply": "-",
    "metadata": {
        "label": "Subtraction (A-B)"
    }
}' where title = 'Local Subtraction';

"""
    ))
}
