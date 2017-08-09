/* globals L, _ */

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
        this.labelInputs = [{'name': 'All', 'id': 0}];
        this.filterLabel = {'name': 'All'};

        this.labelFilter = (annotation) => {
            if (annotation.properties.label === this.filterLabel.name) {
                return true;
            } else if (this.filterLabel.name === 'All') {
                return true;
            }
            return false;
        };

        this.filteredAnnotations = {
            'type': 'FeatureCollection',
            'features': []
        };

        this.$scope.$on('$destroy', this.$onDestroy.bind(this));
    }

    $onDestroy() {
        this.getMap().then((mapWrapper) => {
            mapWrapper.deleteLayers('Annotation');
            mapWrapper.deleteLayers('draw');
        });
    }

    onFilterChange(filterLabel) {
        this.getMap().then((mapWrapper) => {
            mapWrapper.deleteLayers('Annotation');
            if (filterLabel.name !== 'All') {
                this.filteredAnnotations = {
                    'type': 'FeatureCollection',
                    'features': _.filter(this.annoToExport.features, (f) => {
                        return f.properties.label === filterLabel.name;
                    })
                };
                mapWrapper.setLayer(
                    'Annotation',
                    this.createLayersOneByOne(this.filteredAnnotations.features),
                    true
                );
            } else {
                this.filteredAnnotations = {
                    'type': 'FeatureCollection',
                    'features': []
                };
                mapWrapper.setLayer(
                    'Annotation',
                    this.createLayersOneByOne(this.annoToExport.features),
                    true
                );
            }
        });
    }

    createLayersOneByOne(features) {
        return _.map(features, (f) => {
            return this.createLayerWithPopupFromGeojson(
                {'type': 'FeatureCollection', 'features': [f]}
            );
        });
    }

    importLocalAnnotations() {
        // TODO import should change filter to its corresponding label when the filter mode is on
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
                                [-76.97021484375, 39.62261494094297],
                                [-74.718017578125, 39.62261494094297],
                                [-74.718017578125, 40.78885994449482],
                                [-76.97021484375, 40.78885994449482],
                                [-76.97021484375, 39.62261494094297]
                            ]
                        ]
                    }
                }
            ]
        };

        let importedLayer = this.createLayersOneByOne(this.importedAnno.features);
        this.getMap().then((mapWrapper) => {
            mapWrapper.setLayer(
                'Annotation',
                _.union(mapWrapper.getLayers('Annotation'), importedLayer),
                true
            );
        });
        this.annoToExport.features = this.annoToExport.features.concat(this.importedAnno.features);

        this.labelInputs = _.chain(this.annoToExport.features)
                            .map((f) => {
                                return {'name': f.properties.label, 'id': new Date().getTime()};
                            })
                            .uniqBy('name')
                            .value();
        this.labelInputs.push({'name': 'All', 'id': 0});
    }

    onClearAnnotation() {
        this.getMap().then((mapWrapper) => {
            mapWrapper.deleteLayers('Annotation');
        });
        this.annoToExport = {
            'type': 'FeatureCollection',
            features: []
        };
        this.labelInputs = [{'name': 'All', 'id': 0}];
    }

    onAnnotationsExport() {
        if (this.filteredAnnotations.features && this.filteredAnnotations.features.length) {
            this.$log.log(this.filteredAnnotations);
            if (this.filterLabel.name === 'All') {
                this.onClearAnnotation();
            }
        } else if (this.annoToExport.features && this.annoToExport.features.length) {
            this.$log.log(this.annoToExport);
            this.onClearAnnotation();
        } else {
            this.$log.log('Nothing to export.');
        }
    }

    onShapeCreating(isCreating) {
        this.isCreating = isCreating;
    }

    onAddShapeToDrawLayer(shapeLayer, shapeType) {
        this.getMap().then((mapWrapper) => mapWrapper.setLayer('draw', shapeLayer, false));
        this.addNewAnnotationIdToData(shapeType);
    }

    addNewAnnotationIdToData(shapeType) {
        if (this.filterLabel.name === 'All') {
            this.annoToExport.features.push({
                'properties': {
                    'id': new Date().getTime(),
                    'type': shapeType
                }
            });
        } else {
            this.annoToExport.features.push({
                'properties': {
                    'id': new Date().getTime(),
                    'type': shapeType,
                    'label': this.filterLabel.name
                }
            });
        }

        this.isLabeling = true;
    }

    onCancelAddAnnotation(id) {
        _.remove(this.annoToExport.features, (f) => f.properties.id === id);
        this.getMap().then((mapWrapper) => mapWrapper.deleteLayers('draw'));
        this.isLabeling = false;
        this.isCreating = false;
    }

    onAddAnnotation(id, label, description) {
        this.getMap().then((mapWrapper) => {
            let newGeojson = this.createGeojsonWithMetadata(mapWrapper, id, label, description);
            let newLayer = this.createLayerWithPopupFromGeojson(newGeojson);
            let layers = (mapWrapper.getLayers('Annotation') || []).concat(newLayer);

            _.remove(this.annoToExport.features, (f) => {
                return f.properties.id === id;
            });
            this.annoToExport.features.push(newGeojson);

            mapWrapper.setLayer('Annotation', layers, true);
            mapWrapper.deleteLayers('draw');
        });

        this.labelInputs.push({'name': label, 'id': new Date().getTime()});
        this.labelInputs = _.chain(this.labelInputs).uniqBy('name').sortBy('id').value();

        this.isLabeling = false;
        this.isCreating = false;

        if (this.filterLabel.name !== 'All' && this.filterLabel.name !== label) {
            this.filterLabel.name = label;
            this.onFilterChange(this.filterLabel);
        }
    }

    createGeojsonWithMetadata(mapWrapper, id, label, description) {
        let geojson = _.first(mapWrapper.getLayers('draw')).toGeoJSON();
        geojson.properties = {
            'id': id,
            'label': label,
            'description': description
        };
        return geojson;
    }

    createLayerWithPopupFromGeojson(geojsonData) {
        return L.geoJSON(geojsonData, {
            style: () => {
                return {
                    weight: 2,
                    fillOpacity: 0.2,
                    opacity: 0.5
                };
            },
            pointToLayer: (geoJsonPoint, latlng) => {
                return L.marker(latlng, {'icon': L.divIcon({'className': 'annotate-marker'})});
            },
            onEachFeature: (feature, layer) => {
                layer.bindPopup(
                    `
                    <label class="leaflet-popup-label">Label:<br/>
                    <p>${feature.properties.label}</p></label><br/>
                    <label class="leaflet-popup-label">Description:<br/>
                    <p>${feature.properties.description}</p></label>
                    `,
                    {closeButton: false}
                );
            }
        });
    }


    onUpdateAnnotationStart() {
        this.isLabeling = true;
        this.isCreating = true;
    }


    onCancelUpdateAnnotation(id) {
        this.getMap().then((mapWrapper) => {
            let originalData = _.filter(this.annoToExport.features, f => f.properties.id === id);
            let originalDataLayer = this.createLayerWithPopupFromGeojson(originalData[0]);
            let layers = _.union(
                mapWrapper.getLayers('Annotation'),
                [originalDataLayer]
            );
            mapWrapper.setLayer('Annotation', layers, true);
            mapWrapper.deleteLayers('draw');
        });
        this.isLabeling = false;
        this.isCreating = false;
    }

    /* eslint-disable no-underscore-dangle */
    onDeleteAnnotation(id, label) {
        _.remove(this.annoToExport.features, f => f.properties.id === id);

        if (!_.find(this.annoToExport.features, f => f.properties.label === label)) {
            _.remove(this.labelInputs, (i) => i.name === label);
            this.filterLabel = {'name': 'All'};
            this.onFilterChange(this.filterLabel);
        }

        this.getMap().then((mapWrapper) => {
            let annotationLayers = mapWrapper.getLayers('Annotation');

            _.remove(annotationLayers, (l) => {
                return _.first(_.values(l._layers))
                    .feature.properties.id === id;
            });

            mapWrapper.setLayer('Annotation', annotationLayers, true);
        });
    }
    /* eslint-enable no-underscore-dangle */

    /* eslint-disable no-underscore-dangle */
    onUpdateAnnotationFinish(layer, id, label, oldLabel, description) {
        let geojsonData = layer.toGeoJSON();
        geojsonData.features[0].properties = {
            'id': id,
            'label': label,
            'description': description
        };
        let updatedLayer = this.createLayerWithPopupFromGeojson(geojsonData);

        this.getMap().then((mapWrapper) => {
            let layers = _.union(mapWrapper.getLayers('Annotation'), [updatedLayer]);
            mapWrapper.deleteLayers('draw');
            mapWrapper.setLayer('Annotation', layers, true);
        });

        _.remove(this.annoToExport.features, (f) => f.properties.id === id);
        this.annoToExport.features.push(geojsonData.features[0]);

        this.isLabeling = false;
        this.isCreating = false;

        if (label !== oldLabel) {
            if (!_.find(this.annoToExport.features, f => f.properties.label === oldLabel)) {
                _.remove(this.labelInputs, (i) => i.name === oldLabel);
            }
            this.labelInputs.push({'name': label, 'id': new Date().getTime()});
            this.labelInputs = _.chain(this.labelInputs).uniqBy('name').sortBy('id').value();
        }

        if (this.filterLabel.name !== 'All') {
            this.filterLabel = {'name': label};
        }
        this.onFilterChange(this.filterLabel);
    }
    /* eslint-enable no-underscore-dangle */
}
