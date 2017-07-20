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
        this.annoToPass = {
            'type': 'FeatureCollection',
            features: []
        };
        this.annoToExport = {};
        this.getMap().then((mapWrapper) => {
            // TODO figure out why creating and using a new map pane with higher z-index for popups
            // still can't bring the popups up from the annotate toolbar
            mapWrapper.map.createPane('annotationPopups');
            mapWrapper.map.getPane('annotationPopups').style.zIndex = 1050;
        });
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
        this.annoToPass.features = this.importedAnno.features.concat(
            this.annoToExport.features ? this.annoToExport.features : []
        );
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
                            'closeButton': false,
                            'pane': 'annotationPopups'
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
        } else if (this.annoToPass.features && this.annoToPass.features.length) {
            this.$log.log(this.annoToPass);
        } else {
            this.$log.log('Nothing to export.');
        }
        this.getMap().then((mapWrapper) => {
            mapWrapper.deleteLayers('Annotation');
        });
        this.annoToExport = {};
        this.annoToPass = {
            'type': 'FeatureCollection',
            features: []
        };
    }

    onAnnotationSave(data) {
        this.annoToExport = data();
    }
}
