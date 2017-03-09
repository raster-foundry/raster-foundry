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
};
