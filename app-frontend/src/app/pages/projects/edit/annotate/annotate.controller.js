export default class AnnotateController {
    constructor( // eslint-disable-line max-params
        $log, $state, $scope,
        mapService
    ) {
        'ngInject';
        this.$log = $log;
        this.$state = $state;
        this.$scope = $scope;
        this.getMap = () => mapService.getMap('edit');
    }

    $onInit() {
        this.importedAnno = {};
        this.annoToExport = {
            'type': 'FeatureCollection',
            features: []
        };
    }

    importLocalAnnotations() {
        this.importedAnno = {
            'type': 'FeatureCollection',
            'features': [
                {
                    'type': 'Feature',
                    'properties': {
                        'label': 'Philadelphia',
                        'description': 'This is Philly.',
                        'id': new Date().getTime()
                    },
                    'geometry': {
                        'type': 'Polygon',
                        'coordinates': [
                            [
                                [
                                    -76.97021484375,
                                    39.62261494094297
                                ],
                                [
                                    -74.718017578125,
                                    39.62261494094297
                                ],
                                [
                                    -74.718017578125,
                                    40.78885994449482
                                ],
                                [
                                    -76.97021484375,
                                    40.78885994449482
                                ],
                                [
                                    -76.97021484375,
                                    39.62261494094297
                                ]
                            ]
                        ]
                    }
                }
            ]
        };
        this.drawImportedAnnotations(this.importedAnno);
        this.annoToExport.features = this.annoToExport.features.concat(this.importedAnno.features);
    }

    drawImportedAnnotations(geoJsonData) {
        this.getMap().then((mapWrapper)=>{
            let geoJsonLayer = L.geoJSON(geoJsonData, {
                style: () => {
                    return {
                        weight: 2,
                        fillOpacity: 0.2,
                        opacity: 0.5
                    };
                },
                onEachFeature: (feature, layer) => {
                    layer.bindPopup(
                        `
                        <label class="leaflet-popup-label">Label:<br/>
                        <p>${feature.properties.label}</p></label><br/>
                        <label class="leaflet-popup-label">Description:<br/>
                        <p>${feature.properties.description}</p></label>
                        `,
                        {
                            closeButton: false
                        }
                    );
                }
            });
            mapWrapper.addLayer(
                'Annotation',
                geoJsonLayer,
                true
            );
        });
    }

    exportAnnotations() {
        if (this.annoToExport.features && this.annoToExport.features.length) {
            this.$log.log(this.annoToExport);
        } else {
            this.$log.log('Nothing to export.');
        }
        this.getMap().then((mapWrapper) => {
            mapWrapper.deleteLayers('Annotation');
        });
        this.annoToExport = {
            'type': 'FeatureCollection',
            features: []
        };
    }

    onAnnotationSave(data) {
        this.annoToExport = data();
    }
}
