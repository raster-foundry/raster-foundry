/* globals L, _, $ */

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

        this.isUpload = true;

        this.$scope.$on('$destroy', this.$onDestroy.bind(this));

        $('#btn-upload').change((e) => {
            this.uploadedData = _.values(e.target.files);
            if (this.uploadedData.length) {
                this.isUpload = false;
                this.$scope.$evalAsync();
            }
        });
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

    /* eslint-disable no-undef */
    onImportClick() {
        if (this.uploadedData.length) {
            _.forEach(this.uploadedData, (datum) => {
                let reader = new FileReader();
                reader.onload = (e) => this.importLocalAnnotations(JSON.parse(e.target.result));
                reader.readAsText(datum);
            });
            this.isUpload = true;
        }
    }
    /* eslint-enable no-undef */

    importLocalAnnotations(resultData) {
        this.filterLabel.name = 'All';

        _.forEach(resultData.features, (f, i) => {
            f.properties.prevId = f.properties.id;
            f.properties.id = new Date().getTime() + i;
        });

        let importedLayer = this.createLayersOneByOne(resultData.features);
        this.getMap().then((mapWrapper) => {
            mapWrapper.setLayer(
                'Annotation',
                _.union(mapWrapper.getLayers('Annotation'), importedLayer),
                true
            );
        });
        this.annoToExport.features = this.annoToExport.features.concat(resultData.features);

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

    onAnnotationsExport(e) {
        if (this.filteredAnnotations.features && this.filteredAnnotations.features.length) {
            this.onAnnotationsDownload(e, this.filteredAnnotations);
            if (this.filterLabel.name === 'All') {
                this.onClearAnnotation();
            }
        } else if (this.annoToExport.features && this.annoToExport.features.length) {
            this.onAnnotationsDownload(e, this.annoToExport);
            this.onClearAnnotation();
        } else {
            this.onAnnotationsDownload(e, {'result': 'Nothing to export.'});
        }
    }

    onAnnotationsDownload(e, annotationData) {
        let href = `data:text/json;charset=utf-8,${encodeURI(JSON.stringify(annotationData))}`;
        let dl = angular.element(`<a
                       href="${href}"
                       download="rf_annotation_export_${new Date().getTime()}.geojson"></a>`);
        angular.element(e.target).parent().append(dl);
        dl[0].click();
        dl.remove();
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
