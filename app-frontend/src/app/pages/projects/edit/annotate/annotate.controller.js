/* globals L, _, $ */
const RED = '#E57373';
const BLUE = '#3388FF';

export default class AnnotateController {
    constructor( // eslint-disable-line max-params
        $log, $state, $scope, $anchorScroll, $location,
        mapService
    ) {
        'ngInject';
        this.$log = $log;
        this.$state = $state;
        this.$scope = $scope;
        this.$anchorScroll = $anchorScroll;
        this.$location = $location;

        this.getMap = () => mapService.getMap('edit');
    }

    $onInit() {
        L.drawLocal.edit.handlers.edit.tooltip.subtext = '';

        this.setExportDataInitialValues();

        this.setLabelNameFilter();

        this.isUpload = true;
        this.bindUploadEvent();

        this.$scope.$on('$destroy', this.$onDestroy.bind(this));
    }

    /* eslint-disable no-underscore-dangle */
    $onDestroy() {
        this.getMap().then((mapWrapper) => {
            mapWrapper.deleteLayers('Annotation');
            mapWrapper.deleteLayers('draw');
        });
        if (this.editHandler && this.editHandler._enabled) {
            this.editHandler.disable();
        }
    }
    /* eslint-enable no-underscore-dangle */

    setExportDataInitialValues() {
        this.annoToExport = {
            'type': 'FeatureCollection',
            features: []
        };
        this.filteredAnnotations = {
            'type': 'FeatureCollection',
            'features': []
        };
    }

    setLabelNameFilter() {
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
    }

    bindUploadEvent() {
        $('#btn-upload').change((e) => {
            this.uploadedData = _.values(e.target.files);
            if (this.uploadedData.length) {
                this.isUpload = false;
                this.$scope.$evalAsync();
            }
        });
    }

    updateLabelInputs(features) {
        return [{'name': 'All', 'id': 0}].concat(
            _.chain(features)
            .map(f => {
                return {
                    'name': f.properties.label,
                    'id': f.properties.label + (new Date().getTime()).toString()
                };
            })
            .uniqBy('name')
            .sortBy('id')
            .value()
        );
    }

    importLocalAnnotations(data) {
        this.filterLabel.name = 'All';
        this.onFilterChange(this.filterLabel);

        _.forEach(data.features, (f, i) => {
            f.properties.id = new Date().getTime() + i;
        });

        this.annoToExport.features = this.annoToExport.features.concat(data.features);

        this.getMap().then((mapWrapper) => {
            mapWrapper.setLayer(
                'Annotation',
                this.createLayerWithPopupFromGeojson(this.annoToExport),
                true
            );
        });

        this.labelInputs = this.updateLabelInputs(this.annoToExport.features);
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

    onAnnotationsDownload(e, annotationData) {
        let href = `data:text/json;charset=utf-8,${encodeURI(JSON.stringify(annotationData))}`;
        let dl = angular.element(`<a href="${href}"
            download="rf_annotation_export_${new Date().getTime()}.geojson"></a>`);
        angular.element(e.target).parent().append(dl);
        dl[0].click();
        dl.remove();
    }

    onAnnotationsExport(e) {
        if (this.filteredAnnotations.features && this.filteredAnnotations.features.length) {
            this.onAnnotationsDownload(
                e,
                this.createExportData(this.filteredAnnotations, ['id', 'prevId'])
            );
            if (this.filterLabel.name === 'All') {
                this.onClearAnnotation();
            }
        } else if (this.annoToExport.features && this.annoToExport.features.length) {
            this.onAnnotationsDownload(
                e,
                this.createExportData(this.annoToExport, ['id', 'prevId'])
            );
            this.onClearAnnotation();
        } else {
            this.onAnnotationsDownload(e, {'result': 'Nothing to export.'});
        }
    }

    createExportData(data, omitKeys) {
        let propertyKeys = _.difference(
            _.keys(data.features[0].properties),
            omitKeys
        );
        return {
            features: data.features.map((f) => {
                return {
                    geometry: f.geometry,
                    properties: _.pick(f.properties, propertyKeys),
                    type: 'Feature'
                };
            }),
            type: 'FeatureCollection'
        };
    }

    /* eslint-disable no-underscore-dangle */
    onFilterChange(filterLabel) {
        this.getMap().then((mapWrapper) => {
            if (filterLabel.name !== 'All') {
                this.filteredAnnotations = {
                    'type': 'FeatureCollection',
                    'features': _.filter(this.annoToExport.features, (f) => {
                        return f.properties.label === filterLabel.name;
                    })
                };
                mapWrapper.setLayer(
                    'Annotation',
                    this.createLayerWithPopupFromGeojson(this.filteredAnnotations),
                    true
                );
            } else {
                this.filteredAnnotations = {
                    'type': 'FeatureCollection',
                    'features': []
                };
                mapWrapper.setLayer(
                    'Annotation',
                    this.createLayerWithPopupFromGeojson(this.annoToExport),
                    true
                );
            }
            if (!_.isEmpty(mapWrapper.getLayers('Annotation')[0]._layers)) {
                mapWrapper.map.fitBounds(
                    L.featureGroup(mapWrapper.getLayers('Annotation')).getBounds()
                );
            }
        });
    }
    /* eslint-enable no-underscore-dangle */

    onShapeCreating(isCreating) {
        this.isCreating = isCreating;
        this.disableSidebarAction = isCreating;
    }

    setAndEnableEditHandler(mapWrapper, editLayer) {
        this.editHandler = new L.EditToolbar.Edit(mapWrapper.map, {
            featureGroup: editLayer
        });
        this.editHandler.enable();
    }

    createEditableDrawLayer(mapWrapper, geometry) {
        if (geometry.type === 'Polygon') {
            let coordinates = geometry.coordinates[0].map(c => [c[1], c[0]]);
            let polygonLayer = L.polygon(
                coordinates,
                {
                    'draggable': true,
                    'fillColor': RED,
                    'color': RED,
                    'opacity': 0.5
                }
            );
            mapWrapper.setLayer('draw', polygonLayer, false);
            mapWrapper.map.panTo(polygonLayer.getCenter());
            this.setAndEnableEditHandler(mapWrapper, L.featureGroup([polygonLayer]));
        } else if (geometry.type === 'Point') {
            let markerLayer = L.marker([geometry.coordinates[1], geometry.coordinates[0]], {
                'icon': L.divIcon({
                    'className': 'annotate-clone-marker'
                }),
                'draggable': true
            });
            mapWrapper.setLayer('draw', markerLayer, false);
            mapWrapper.map.panTo([geometry.coordinates[1], geometry.coordinates[0]]);
        }
    }

    addNewAnnotationIdToData() {
        let id = new Date().getTime();
        if (this.filterLabel.name === 'All') {
            this.annoToExport.features.push({
                'properties': {
                    'id': id,
                    'label': '',
                    'description': ''
                }
            });
        } else {
            this.annoToExport.features.push({
                'properties': {
                    'id': id,
                    'label': this.filterLabel.name,
                    'description': ''
                }
            });
        }
        this.disableToolbarAction = true;
        this.scrollToItem(id);
    }

    onShapeCreated(shapeLayer) {
        this.getMap().then((mapWrapper) => {
            let geojsonData = shapeLayer.toGeoJSON();
            this.createEditableDrawLayer(mapWrapper, geojsonData.geometry);
            this.addNewAnnotationIdToData();
        });
    }

    createGeojsonFromDrawLayer(mapWrapper, id, label, description) {
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
                    opacity: 0.8,
                    color: BLUE,
                    fillColor: BLUE
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
                    <p>${feature.properties.description || 'No description'}</p></label>
                    `,
                    {closeButton: false}
                ).on('mouseover', (e) => {
                    if (!this.disableSidebarAction && !this.clickedId) {
                        this.hoveredId = feature.properties.id;
                        this.setLayerStyle(feature, e, RED, 'annotate-hover-marker');
                        this.scrollToItem(this.hoveredId);
                        this.$scope.$evalAsync();
                    }
                }).on('mouseout', (e) => {
                    if (!this.disableSidebarAction && !this.clickedId) {
                        delete this.hoveredId;
                        this.setLayerStyle(feature, e, BLUE, 'annotate-marker');
                        this.$scope.$evalAsync();
                    }
                }).on('click', (e) => {
                    this.getMap().then((mapWrapper) => {
                        mapWrapper.map.panTo(e.target.getBounds().getCenter());
                    });
                    if (!this.disableSidebarAction) {
                        if (!this.clickedId) {
                            this.clickedId = feature.properties.id;
                            this.setLayerStyle(feature, e, RED, 'annotate-hover-marker');
                            this.scrollToItem(this.clickedId);
                            this.$scope.$evalAsync();
                        } else {
                            delete this.clickedId;
                            this.setLayerStyle(feature, e, BLUE, 'annotate-marker');
                            this.$scope.$evalAsync();
                        }
                    }
                });
            }
        });
    }

    setLayerStyle(feature, e, color, iconClass) {
        if (feature.geometry.type === 'Polygon') {
            e.target.setStyle({'color': color});
        } else if (feature.geometry.type === 'Point') {
            e.target.setIcon(L.divIcon({'className': iconClass}));
        }
    }

    scrollToItem(id) {
        let newHash = 'anchor' + id;
        if (this.$location.hash() !== newHash) {
            this.$location.hash(newHash);
        } else {
            this.$anchorScroll();
        }
    }

    /* eslint-disable no-underscore-dangle */
    onUpdateAnnotationFinish(id, label, description, isEdit) {
        if (isEdit) {
            delete this.editId;
        }
        this.disableSidebarAction = false;
        this.disableToolbarAction = false;
        this.isCreating = false;

        if (this.editHandler && this.editHandler._enabled) {
            this.editHandler.disable();
        }

        this.getMap().then((mapWrapper) => {
            _.forEach(this.annoToExport.features, (f) => {
                if (f.properties.id === id) {
                    Object.assign(f, this.createGeojsonFromDrawLayer(
                        mapWrapper, id, label, description
                    ));
                }
            });

            mapWrapper.setLayer(
                'Annotation',
                this.createLayerWithPopupFromGeojson(this.annoToExport),
                true
            );
            mapWrapper.deleteLayers('draw');

            this.labelInputs = this.updateLabelInputs(this.annoToExport.features);

            if (this.filterLabel.name !== 'All' && this.filterLabel.name !== label) {
                this.filterLabel.name = label;
                this.onFilterChange(this.filterLabel);
            } else if (this.filterLabel.name === label) {
                this.onFilterChange(this.filterLabel);
            }
        });
    }
    /* eslint-enable no-underscore-dangle */

    onCloneAnnotation(geometry, label, description) {
        this.disableSidebarAction = true;
        this.disableToolbarAction = true;
        this.getMap().then((mapWrapper) => {
            this.createEditableDrawLayer(mapWrapper, geometry);
        });

        let id = new Date().getTime();
        this.annoToExport.features.push({
            'properties': {
                'id': id,
                'label': label,
                'description': description
            }
        });
        this.scrollToItem(id);
    }

    onUpdateAnnotationStart(annotation) {
        this.disableToolbarAction = true;
        this.isCreating = true;
        this.disableSidebarAction = true;
        this.editId = annotation.properties.id;

        this.scrollToItem(this.editId);

        this.getMap().then((mapWrapper) => {
            this.createEditableDrawLayer(mapWrapper, annotation.geometry);
            let otherAnnoGeojson = _.filter(this.annoToExport.features, (f) => {
                return f.properties.id !== annotation.properties.id;
            });
            let otherAnnoMatchedLabelGeojson = _.filter(otherAnnoGeojson, (f) => {
                return f.properties.label === annotation.properties.label;
            });
            mapWrapper.setLayer(
                'Annotation',
                this.createLayerWithPopupFromGeojson({
                    'type': 'FeatureCollection',
                    features: this.filterLabel.name === 'All' ? otherAnnoGeojson :
                        otherAnnoMatchedLabelGeojson
                }),
                true
            );
        });
    }

    updateFilterAndMapRender(label) {
        if (this.filterLabel.name !== 'All'
            && this.filterLabel.name !== label
        ) {
            this.filterLabel.name = label;
            this.onFilterChange(this.filterLabel);
        } else if (this.filterLabel.name === label) {
            this.onFilterChange(this.filterLabel);
        }
    }

    /* eslint-disable no-underscore-dangle */
    onCancelUpdateAnnotation(annotation, isEdit) {
        this.disableToolbarAction = false;
        this.disableSidebarAction = false;
        this.isCreating = false;
        if (this.editHandler && this.editHandler._enabled) {
            this.editHandler.disable();
        }

        if (isEdit) {
            delete this.editId;

            this.getMap().then((mapWrapper) => {
                mapWrapper.setLayer(
                    'Annotation',
                    this.createLayerWithPopupFromGeojson(this.annoToExport),
                    true
                );
                mapWrapper.deleteLayers('draw');

                this.updateFilterAndMapRender(annotation.properties.label);
            });
        } else {
            _.remove(this.annoToExport.features, (f) => {
                return f.properties.id === annotation.properties.id;
            });
            this.getMap().then((mapWrapper) => mapWrapper.deleteLayers('draw'));
        }
    }
    /* eslint-enable no-underscore-dangle */

    onDeleteAnnotation(id, label) {
        _.remove(this.annoToExport.features, f => f.properties.id === id);

        this.getMap().then((mapWrapper) => {
            mapWrapper.setLayer(
                'Annotation',
                this.createLayerWithPopupFromGeojson(this.annoToExport),
                true
            );

            this.labelInputs = this.updateLabelInputs(this.annoToExport.features);

            if (!_.find(this.labelInputs, i => i.name === label)) {
                this.filterLabel = {'name': 'All'};
                this.onFilterChange(this.filterLabel);
            }

            this.updateFilterAndMapRender(label);

            mapWrapper.deleteLayers('highlight');
        });
    }

    getAnnotationId(annotation) {
        return `anchor${annotation.properties.id}`;
    }

    toggleSidebarItemClick(annotation) {
        if (!this.disableSidebarAction) {
            if (!this.clickedId) {
                this.clickedId = annotation.properties.id;
                this.addAndPanToHighlightLayer(annotation, true);
            } else {
                delete this.clickedId;
                this.deleteHighlightLayer();
            }
        }
    }

    onSidebarItemMouseIn(annotation) {
        if (!this.disableSidebarAction && !this.clickedId) {
            this.hoveredId = annotation.properties.id;
            this.addAndPanToHighlightLayer(annotation, true);
        }
    }

    onSidebarItemMouseOut() {
        if (!this.disableSidebarAction && !this.clickedId) {
            delete this.hoveredId;
            this.deleteHighlightLayer();
        }
    }

    deleteHighlightLayer() {
        this.getMap().then((mapWrapper) => {
            mapWrapper.deleteLayers('highlight');
        });
    }

    createHighlightLayer(data) {
        return L.geoJSON(data, {
            style: () => {
                return {'color': RED, 'opacity': 0.8, 'fillOpacity': 0};
            },
            pointToLayer: (geoJsonPoint, latlng) => {
                return L.marker(latlng, {
                    'icon': L.divIcon({'className': 'annotate-highlight-marker'})
                });
            }
        });
    }

    addAndPanToHighlightLayer(annotation, isPan) {
        this.getMap().then((mapWrapper) => {
            let highlightLayer = this.createHighlightLayer(annotation);
            mapWrapper.setLayer('highlight', highlightLayer, false);
            if (isPan) {
                mapWrapper.map.panTo(highlightLayer.getBounds().getCenter(), {'duration': 0.75});
            }
        });
    }
}
