/* globals L, _, $ */
import angular from 'angular';
require('./annotate.scss');

const RED = '#E57373';
const BLUE = '#3388FF';

class AnnotateController {
    constructor( // eslint-disable-line max-params
        $log, $state, $scope, $rootScope, $anchorScroll, $timeout, $element, $window,
        mapService, hotkeys
    ) {
        'ngInject';
        this.$log = $log;
        this.$state = $state;
        this.$scope = $scope;
        this.$rootScope = $rootScope;
        this.$anchorScroll = $anchorScroll;
        this.$timeout = $timeout;
        this.$element = $element;
        this.$window = $window;
        this.hotkeys = hotkeys;

        this.getMap = () => mapService.getMap('edit');
    }

    $onInit() {
        L.drawLocal.edit.handlers.edit.tooltip.subtext = '';

        this.setExportDataInitialValues();

        this.setLabelNameFilter();

        this.bindHotkeys();

        this.bindReloadAlert();

        this.$element.on('click', () => {
            this.deleteClickedHighlight();
        });

        this.$scope.$on('$destroy', this.$onDestroy.bind(this));
    }

    $onDestroy() {
        this.getMap().then((mapWrapper) => {
            mapWrapper.deleteLayers('Annotation');
            mapWrapper.deleteLayers('draw');
            mapWrapper.deleteLayers('highlight');
            this.disableTransformHandler(mapWrapper);
        });
        this.disableEditHandler();
        this.$window.removeEventListener('beforeunload', this.onWindowUnload);
    }

    bindReloadAlert() {
        this.onWindowUnload = (event) => {
            if (this.annoToExport.features.length) {
                event.returnValue =
                    'Leaving this page will delete all unexported annotations. Are you sure?';
            }
        };
        this.$window.addEventListener('beforeunload', this.onWindowUnload);
    }

    deleteClickedHighlight() {
        delete this.hoveredId;
        delete this.clickedId;
        this.getMap().then((mapWrapper) => {
            mapWrapper.deleteLayers('highlight');
        });
    }

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

    bindHotkeys() {
        this.hotkeys
            .bindTo(this.$scope)
            .add({
                combo: 'n',
                description: 'Create new rectangle',
                callback: () => {
                    if (!this.disableToolbarAction) {
                        if (angular.element('.btn-anno-rectangle')) {
                            this.$timeout(() => angular.element('.btn-anno-rectangle').click());
                        }
                    }
                }
            })
            .add({
                combo: 'p',
                description: 'Create new polygon',
                callback: () => {
                    if (!this.disableToolbarAction) {
                        if (angular.element('.btn-anno-polygon')) {
                            this.$timeout(() => angular.element('.btn-anno-polygon').click());
                        }
                    }
                }
            })
            .add({
                combo: 'm',
                description: 'Create new point',
                callback: () => {
                    if (!this.disableToolbarAction) {
                        if (angular.element('.btn-anno-point')) {
                            this.$timeout(() => angular.element('.btn-anno-point').click());
                        }
                    }
                }
            })
            .add({
                combo: 'c',
                description: 'Clone annotation',
                callback: () => {
                    if (this.clickedId) {
                        let clickedData = _.filter(this.annoToExport.features, (f) => {
                            return f.properties.id === this.clickedId;
                        })[0];
                        this.onCloneAnnotation(
                            clickedData.geometry,
                            clickedData.properties.label,
                            clickedData.properties.description
                        );
                    }
                }
            })
            .add({
                combo: 'e',
                description: 'Edit annotation',
                callback: () => {
                    this.getMap().then((mapWrapper) => {
                        let drawLayer = mapWrapper.getLayers('draw')[0];
                        if (!_.isEmpty(drawLayer) && drawLayer.transform) {
                            this.disableTransformHandler(mapWrapper);
                            let drawLayerGeometry = drawLayer.toGeoJSON().geometry;
                            this.createEditableDrawLayer(mapWrapper, drawLayerGeometry);
                        } else if (this.clickedId) {
                            this.onUpdateAnnotationStart(
                              this.annoToExport.features.find((f) => {
                                  return f.properties.id === this.clickedId;
                              })
                            );
                        }
                    });
                }
            })
            .add({
                combo: 'd',
                description: 'Delete annotation',
                callback: () => {
                    if (this.clickedId) {
                        this.onDeleteAnnotation(
                            this.clickedId,
                            _.filter(this.annoToExport.features, (f) => {
                                return f.properties.id === this.clickedId;
                            })[0].properties.label
                        );
                    }
                }
            })
            .add({
                combo: 'r',
                description: 'Rotate/rescale annotation shape',
                callback: () => {
                    this.getMap().then((mapWrapper) => {
                        this.createRotatableLayerFromDrawLayer(mapWrapper);
                    });
                }
            })
            .add({
                combo: 'up',
                description: 'Move annotation north',
                callback: () => {
                    this.getMap().then((mapWrapper) => {
                        this.onMoveAnnotation(mapWrapper, 'up');
                    });
                }
            })
            .add({
                combo: 'down',
                description: 'Move annotation south',
                callback: () => {
                    this.getMap().then((mapWrapper) => {
                        this.onMoveAnnotation(mapWrapper, 'down');
                    });
                }
            })
            .add({
                combo: 'left',
                description: 'Move annotation west',
                callback: () => {
                    this.getMap().then((mapWrapper) => {
                        this.onMoveAnnotation(mapWrapper, 'left');
                    });
                }
            })
            .add({
                combo: 'right',
                description: 'Move annotation east',
                callback: () => {
                    this.getMap().then((mapWrapper) => {
                        this.onMoveAnnotation(mapWrapper, 'right');
                    });
                }
            })
            .add({
                combo: 'shift+return',
                allowIn: ['INPUT', 'TEXTAREA'],
                description: 'Submit annotation',
                callback: () => {
                    if (angular.element('.annotation-confirm')) {
                        this.$timeout(() => angular.element('.annotation-confirm').click());
                    }
                }
            })
            .add({
                combo: 'esc',
                allowIn: ['INPUT', 'TEXTAREA'],
                description: 'Cancel submitting annotation',
                callback: () => {
                    if (angular.element('.annotation-cancel')) {
                        this.$timeout(() => angular.element('.annotation-cancel').click());
                    }
                    if (!this.disableToolbarAction) {
                        if (angular.element('.btn-anno-cancel')) {
                            this.$timeout(() => angular.element('.btn-anno-cancel').click());
                        }
                    }
                }
            });
    }

    onMoveAnnotation(mapWrapper, arrowKey) {
        this.disableEditHandler();
        this.disableTransformHandler(mapWrapper);
        let drawLayer = mapWrapper.getLayers('draw')[0];
        if (!_.isEmpty(drawLayer)) {
            let drawLayerGeojson = drawLayer.toGeoJSON();
            let coordinates =
                _.map(drawLayerGeojson.geometry.coordinates[0], (c) => {
                    let point = mapWrapper.map.latLngToContainerPoint(
                        L.latLng(c[1], c[0])
                    );
                    if (arrowKey === 'up') {
                        point.y -= 5;
                    } else if (arrowKey === 'down') {
                        point.y += 5;
                    } else if (arrowKey === 'left') {
                        point.x -= 5;
                    } else if (arrowKey === 'right') {
                        point.x += 5;
                    }
                    let coors = mapWrapper.map.containerPointToLatLng(point);
                    return [coors.lng, coors.lat];
                });
            drawLayerGeojson.geometry.coordinates = [coordinates];
            this.createEditableDrawLayer(
                mapWrapper,
                drawLayerGeojson.geometry
            );
        }
    }

    showHotkeyTips() {
        this.hotkeys.get('?').callback();
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

    onClearAnnotation() {
        this.getMap().then((mapWrapper) => {
            mapWrapper.deleteLayers('Annotation');
            mapWrapper.deleteLayers('draw');
            mapWrapper.deleteLayers('highlight');
        });
        this.setExportDataInitialValues();
        this.setLabelNameFilter();
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

    /* eslint-disable no-underscore-dangle */
    disableEditHandler() {
        if (this.editHandler && this.editHandler._enabled) {
            this.editHandler.disable();
        }
    }
    /* eslint-enable no-underscore-dangle */

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
        this.$anchorScroll('anchor' + id.toString());
        this.$timeout(() => angular.element('#_value').focus());
    }

    onShapeCreated(shapeLayer) {
        this.getMap().then((mapWrapper) => {
            let geojsonData = shapeLayer.toGeoJSON();
            this.createEditableDrawLayer(mapWrapper, geojsonData.geometry);
            this.addNewAnnotationIdToData();
            if (this.bulkTemplate) {
                this.onUpdateAnnotationFinish(
                    this.annoToExport.features.slice(-1)[0].properties.id,
                    this.bulkTemplate.properties.label,
                    this.bulkTemplate.properties.description,
                    false
                );
            }
        });
    }

    createGeojsonFromDrawLayer(mapWrapper, id, label, description) {
        let geojson = _.first(mapWrapper.getLayers('draw')).toGeoJSON();
        geojson.properties = {
            'id': id,
            'label': label,
            'description': description,
            'machineGenerated': false,
            'confidence': null,
            'qualityCheck': null
        };
        return geojson;
    }

    /* eslint-disable no-underscore-dangle */
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
                        this.setLayerStyle(e.target, RED, 'annotate-hover-marker');
                        this.$anchorScroll('anchor' + this.hoveredId.toString());
                        this.$scope.$evalAsync();
                    }
                }).on('mouseout', (e) => {
                    if (!this.disableSidebarAction && !this.clickedId) {
                        delete this.hoveredId;
                        this.setLayerStyle(e.target, BLUE, 'annotate-marker');
                        this.$scope.$evalAsync();
                    }
                }).on('click', (e) => {
                    this.getMap().then((mapWrapper) => {
                        mapWrapper.map.panTo(e.target.getBounds().getCenter());
                        if (!this.disableSidebarAction) {
                            delete this.hoveredId;
                            if (this.clickedId !== feature.properties.id) {
                                let prevLayer = Object
                                    .values(mapWrapper.getLayers('Annotation')[0]._layers)
                                    .filter((l) => l.feature.properties.id === this.clickedId);
                                if (prevLayer.length) {
                                    this.setLayerStyle(prevLayer[0], BLUE, 'annotate-marker');
                                }
                                this.clickedId = feature.properties.id;
                                this.setLayerStyle(e.target, RED, 'annotate-hover-marker');
                                this.$anchorScroll('anchor' + this.clickedId.toString());
                                this.$scope.$evalAsync();
                            } else {
                                delete this.clickedId;
                                this.setLayerStyle(e.target, BLUE, 'annotate-marker');
                                this.$scope.$evalAsync();
                            }
                        }
                    });
                });
            }
        });
    }
    /* eslint-enable no-underscore-dangle */

    setLayerStyle(target, color, iconClass) {
        if (target.feature.geometry.type === 'Polygon') {
            target.setStyle({'color': color});
        } else if (target.feature.geometry.type === 'Point') {
            target.setIcon(L.divIcon({'className': iconClass}));
        }
    }

    onUpdateAnnotationFinish(id, label, description, isEdit) {
        if (isEdit) {
            delete this.editId;
        }
        this.disableSidebarAction = false;
        this.disableToolbarAction = false;
        this.isCreating = false;

        this.disableEditHandler();

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

            this.disableTransformHandler(mapWrapper);
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

    disableTransformHandler(mapWrapper) {
        if (!_.isEmpty(mapWrapper.getLayers('draw')[0])
            && mapWrapper.getLayers('draw')[0].transform) {
            mapWrapper.getLayers('draw')[0].transform.disable();
        }
    }

    onCloneAnnotation(geometry, label, description) {
        this.deleteClickedHighlight();

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
                'description': description,
                'machineGenerated': false,
                'confidence': null,
                'qualityCheck': null
            }
        });
        this.$timeout(() => {
            this.$anchorScroll('anchor' + id.toString());
            angular.element('#_values').focus();
        });
    }

    onUpdateAnnotationStart(annotation) {
        this.getMap().then((mapWrapper) => {
            this.deleteClickedHighlight();
            this.disableTransformHandler(mapWrapper);

            this.disableToolbarAction = true;
            this.isCreating = true;
            this.disableSidebarAction = true;
            this.editId = annotation.properties.id;

            this.$anchorScroll('anchor' + this.editId.toString());

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
        this.$timeout(() => angular.element('#_values').focus());
    }

    onBulkCreate(annotation) {
        this.bulkTemplate = annotation;
    }

    onBulkCreateFinish() {
        this.bulkTemplate = false;
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

    disableActionsOnCancel() {
        this.disableToolbarAction = false;
        this.disableSidebarAction = false;
        this.isCreating = false;
        this.disableEditHandler();
        this.getMap().then((mapWrapper) => {
            this.disableTransformHandler(mapWrapper);
        });
    }

    onCancelUpdateAnnotation(annotation, isEdit) {
        this.isEdit = isEdit;

        if (isEdit) {
            let answer = this.$window.confirm('Cancel Editing This Annotation?');
            if (answer) {
                this.disableActionsOnCancel();
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
            }
        } else {
            let answer = this.$window.confirm('Cancel Adding This Annotation?');
            if (answer) {
                this.disableActionsOnCancel();
                _.remove(this.annoToExport.features, (f) => {
                    return f.properties.id === annotation.properties.id;
                });
                this.getMap().then((mapWrapper) => mapWrapper.deleteLayers('draw'));
            }
        }
    }


    onDeleteAnnotation(id, label) {
        let answer = this.$window.confirm('Delete This Annotation?');
        if (answer) {
            this.deleteClickedHighlight();

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
    }

    toggleSidebarItemClick($event, annotation) {
        $event.stopPropagation();
        if (!this.disableSidebarAction) {
            delete this.hoveredId;
            if (this.clickedId !== annotation.properties.id) {
                this.clickedId = annotation.properties.id;
                this.addAndPanToHighlightLayer(annotation, true);
            } else {
                this.deleteClickedHighlight();
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
            this.deleteClickedHighlight();
        }
    }

    createHighlightLayer(data) {
        return L.geoJSON(data, {
            style: () => {
                return {'color': RED, 'weight': 2, 'opacity': 0.8, 'fillOpacity': 0};
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

    createRotatableLayerFromDrawLayer(mapWrapper) {
        this.disableEditHandler();
        this.disableTransformHandler(mapWrapper);
        mapWrapper.deleteLayers('highlight');

        let drawLayer = _.first(mapWrapper.getLayers('draw'));
        if (drawLayer) {
            let drawLayerGeojson = drawLayer.toGeoJSON();
            if (drawLayerGeojson.geometry.type === 'Polygon') {
                let coordinates = _.map(drawLayerGeojson.geometry.coordinates[0], (c) => {
                    return [c[1], c[0]];
                });
                let polygonLayer = L.polygon(
                    coordinates,
                    {
                        'transform': true,
                        'fillColor': '#ff4433',
                        'color': '#ff4433',
                        'opacity': 0.5
                    }
                );
                mapWrapper.setLayer('draw', polygonLayer, false);
                polygonLayer.transform.enable({rotation: true, scaling: true});
                mapWrapper.map.panTo(polygonLayer.getCenter());
            }
        }
    }

    onQaChecked(annotation, qa) {
        this.annoToExport.features.forEach((f) => {
            if (f.properties.id === annotation.properties.id) {
                f.properties.qualityCheck = qa;
            }
        });
    }
}

const AnnotateModule = angular.module('pages.projects.edit.annotate', ['cfp.hotkeys']);

AnnotateModule.controller(
    'AnnotateController', AnnotateController
);

export default AnnotateModule;
