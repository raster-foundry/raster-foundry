/* eslint-disable */
export const coreTools = {
    "-": {
        "id": "_subtraction",
        "label": "subtraction",
        "args": [
            {
                "type": "raster"
            },
            {
                "type": "raster"
            }
        ]
    },
    "+": {
        "id": "_addition",
        "label": "addition",
        "args": [
            {
                "type": "raster"
            },
            {
                "type": "raster"
            }
        ]
    },
    "/": {
        "id": "_division",
        "label": "division",
        "args": [
            {
                "type": "raster"
            },
            {
                "type": "raster"
            }
        ]
    }
};

export const compressedTool = {
    "apply": "-",
    "tag": "final",
    "label": "Vegetation Change Test",
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
};

export const tool1 = {
	"apply": "+",
	"args": [{
		"id": "a9f4066b-a783-46b6-997d-a0e0d377a1ec",
		"label": null
	}, {
		"id": "580dc0c8-9454-43f5-b72f-1854d7c18e13",
		"label": null
	}],
	"id": "e76c3198-93a0-4a22-9d58-4ba645f8d6f4",
	"label": "a9f4066b-a783-46b6-997d-a0e0d377a1ec_+_580dc0c8-9454-43f5-b72f-1854d7c18e13"
};

export const tool2 = {
	"args": [{
		"apply": "-",
		"args": [{
			"apply": "+",
			"args": [{
				"id": "ff482867-d3de-41ad-a4a0-bdbec3bfdddf",
				"label": null
			}, {
				"id": "bc8e876e-f74b-44ac-b8ff-90ae7192199a",
				"label": null
			}],
			"id": "96795628-ad52-4502-9da4-34f3ce89f2e9",
			"label": "ff482867-d3de-41ad-a4a0-bdbec3bfdddf_+_bc8e876e-f74b-44ac-b8ff-90ae7192199a"
		}, {
			"apply": "/",
			"args": [{
				"apply": "*",
				"args": [{
					"id": "b70af4fe-4632-4d8c-ad0f-5cad87bc81c2",
					"label": null
				}, {
					"id": "a8419d3d-177a-47f5-a615-9235ab35da74",
					"label": null
				}],
				"id": "e8073a25-38a7-40df-bd7c-7bafff893d7d",
				"label": "b70af4fe-4632-4d8c-ad0f-5cad87bc81c2_*_a8419d3d-177a-47f5-a615-9235ab35da74"
			}, {
				"id": "e818a082-02c5-41ea-a022-fd46db08f528",
				"label": null
			}],
			"id": "7058956a-3a9f-4c08-806a-02f1a3af8d23",
			"label": "b70af4fe-4632-4d8c-ad0f-5cad87bc81c2_*_a8419d3d-177a-47f5-a615-9235ab35da74_/_e818a082-02c5-41ea-a022-fd46db08f528"
		}],
		"id": "3f69849b-4888-4176-b98a-96d959130d45",
		"label": "ff482867-d3de-41ad-a4a0-bdbec3bfdddf_+_bc8e876e-f74b-44ac-b8ff-90ae7192199a_-_b70af4fe-4632-4d8c-ad0f-5cad87bc81c2_*_a8419d3d-177a-47f5-a615-9235ab35da74_/_e818a082-02c5-41ea-a022-fd46db08f528"
	}],
	"id": "fda72534-0b3d-4315-9eaa-fb69b960f1f5",
	"label": "Classify(ff482867-d3de-41ad-a4a0-bdbec3bfdddf_+_bc8e876e-f74b-44ac-b8ff-90ae7192199a_-_b70af4fe-4632-4d8c-ad0f-5cad87bc81c2_*_a8419d3d-177a-47f5-a615-9235ab35da74_/_e818a082-02c5-41ea-a022-fd46db08f528)",
	"classBreaks": {
		"classMap": {
			"6.0": 77
		},
		"options": {
			"boundaryType": "lessThanOrEqualTo",
			"ndValue": 123,
			"fallback": -2147483648
		}
	}
};
