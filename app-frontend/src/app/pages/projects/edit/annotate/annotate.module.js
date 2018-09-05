/* globals L, _, $ */
import angular from 'angular';
import { Set } from 'immutable';
import turfCenter from '@turf/center';
require('./annotate.scss');

import AnnotationActions from '_redux/actions/annotation-actions';
import {propertiesToAnnotationFeature, wrapFeatureCollection} from '_redux/annotation-utils';

const RED = '#E57373';
const BLUE = '#3388FF';

class AnnotateController {
    constructor( // eslint-disable-line max-params
        $log, $state, $scope, $rootScope, $anchorScroll, $timeout, $element, $window, $ngRedux,
        mapService, hotkeys, localStorage
    ) {
        'ngInject';
        $scope.autoInject(this, arguments);

        let unsubscribe = $ngRedux.connect(
            this.mapStateToThis,
            AnnotationActions
        )(this);
        $scope.$on('$destroy', unsubscribe);

        this.getMap = () => mapService.getMap('edit');
    }

    mapStateToThis(state) {
        // listen for changes for the shapefile upload so you can know when that's done
        // and display the posted annotations
        let filter = state.projects.filter;
        const annotations = state.projects.annotations;
        let visibleAnnotations = annotations ? annotations.toArray() : [];
        if (filter === 'Unlabeled') {
            visibleAnnotations = visibleAnnotations.filter(
                (annotation) => annotation.properties.label === ''
            );
        } else if (filter !== 'All') {
            visibleAnnotations = visibleAnnotations.filter(
                (annotation) => annotation.properties.label === filter
            );
        }
        let persistedLabels = state.projects.labels;
        let labels = [{name: 'All'}].concat(persistedLabels.map(label => ({name: label})));
        let annotationShapefileProps = state.projects.annotationShapefileProps;
        return {
            user: state.api.user,
            annotations,
            visibleAnnotations,
            labels,
            filter,
            fetchingAnnotations: state.projects.fetchingAnnotations,
            fetchingAnnotationsError: state.projects.fetchingAnnotationsError,
            editingAnnotation: state.projects.editingAnnotation,
            annotationTemplate: state.projects.annotationTemplate,
            annotationShapefileProps
        };
    }

    $onInit() {
        L.drawLocal.edit.handlers.edit.tooltip.subtext = '';

        this.clearLabelFilter();

        this.bindHotkeys();

        this.$element.on('click', () => {
            this.deleteClickedHighlight();
        });

        this.$scope.$on('$destroy', this.$onDestroy.bind(this));

        let userListener = this.$scope.$watch('$ctrl.user', (user) => {
            if (user) {
                this.fetchAnnotations();
                this.fetchLabels();
                this.$scope.$watch('$ctrl.visibleAnnotations', (annotations) => {
                    if (annotations) {
                        this.syncMapWithState();
                    }
                });
                userListener();
            }
        });
        this.$scope.$watch('$ctrl.editingAnnotation', (id) => {
            if (id) {
                this.$scope.$evalAsync(()=>{
                    this.$anchorScroll('anchor' + id);
                });
            }
        });
    }

    $onDestroy() {
        if (this.editingAnnotation) {
            this.finishEditingAnnotation();
        }
        this.getMap().then((mapWrapper) => {
            this.annotations.forEach((annotation, id) => mapWrapper.deleteGeojson(id));
            mapWrapper.deleteLayers('draw');
            mapWrapper.deleteLayers('highlight');
        });
    }

    retryFetches() {
        if (this.user) {
            this.fetchAnnotations();
            this.fetchLabels();
        }
    }

    syncMapWithState() {
        this.getMap().then(mapWrapper => {
            // eslint-disable-next-line
            let mappedAnnotations = mapWrapper._geoJsonMap.keySeq().toSet();
            let newAnnotations = new Set(this.visibleAnnotations.map(annotation => annotation.id));
            if (this.editingAnnotation) {
                newAnnotations = newAnnotations.delete(this.editingAnnotation);
            }
            let annotationsToRemove = mappedAnnotations.subtract(newAnnotations).toArray();
            annotationsToRemove.forEach(id => mapWrapper.deleteGeojson(id));
            // set all, because they might have updated
            newAnnotations
                .map((id) => this.getAnnotationOptions(this.annotations.get(id)))
                .forEach(({annotation, options}) => {
                    mapWrapper.setGeojson(annotation.id, annotation, options);
                });
        });
    }

    getAnnotationOptions(annotation) {
        return {
            annotation,
            options: {
                pointToLayer: (geoJsonPoint, latlng) => {
                    return L.marker(latlng, {'icon': L.divIcon({'className': 'annotate-marker'})});
                },
                onEachFeature: (feature, currentLayer) => {
                    currentLayer.bindPopup(
                        `
                    <label class="leaflet-popup-label">Label:<br/>
                    <p>${feature.properties.label}</p></label><br/>
                    <label class="leaflet-popup-label">Description:<br/>
                    <p>${feature.properties.description || 'No description'}</p></label>
                    `,
                        {closeButton: false}
                    ).on('mouseover', (e) => {
                        if (!this.sidebarDisabled && !this.clickedId && !this.editingAnnotation) {
                            this.hoveredId = feature.id;
                            this.setLayerStyle(e.target, RED, 'annotate-hover-marker');
                            this.$anchorScroll('anchor' + this.hoveredId.toString());
                            this.$scope.$evalAsync();
                        }
                    }).on('mouseout', (e) => {
                        if (!this.sidebarDisabled && !this.clickedId) {
                            delete this.hoveredId;
                            this.setLayerStyle(e.target, BLUE, 'annotate-marker');
                            this.$scope.$evalAsync();
                        }
                    }).on('click', (e) => {
                        this.getMap().then((mapWrapper) => {
                            mapWrapper.map.panTo(e.target.getBounds().getCenter());
                            if (!this.sidebarDisabled && !this.editingAnnotation) {
                                delete this.hoveredId;
                                if (this.clickedId !== feature.id) {
                                    const resetPreviousLayerStyle = () => {
                                        let layer = _.first(mapWrapper.getGeojson(this.clickedId));
                                        if (layer) {
                                            this.setLayerStyle(
                                                layer, BLUE, 'annotate-marker'
                                            );
                                        }
                                    };
                                    resetPreviousLayerStyle();
                                    this.clickedId = feature.id;
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
            }
        };
    }

    deleteClickedHighlight() {
        delete this.hoveredId;
        delete this.clickedId;
        this.getMap().then((mapWrapper) => {
            mapWrapper.deleteLayers('highlight');
        });
    }

    clearLabelFilter() {
        this.labelInputs = [{'name': 'All', 'id': 0}];
        this.filterLabel = {'name': 'All'};
        this.filterAnnotations('All');
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

    onClearAnnotation() {
        let answer = this.$window.confirm('Delete ALL annotations from this project?');
        if (answer) {
            this.getMap().then((mapWrapper) => {
                // eslint-disable-next-line
                let mappedAnnotations = mapWrapper._geoJsonMap.keySeq().toArray();
                mappedAnnotations.forEach(id => mapWrapper.deleteGeojson(id));
                mapWrapper.deleteLayers('draw');
                mapWrapper.deleteLayers('highlight');
                this.clearAnnotations();
            });
            this.clearLabelFilter();
        }
    }

    /* eslint-disable no-underscore-dangle */
    onFilterChange(filterLabel) {
        this.filterAnnotations(filterLabel.name);
    }
    /* eslint-enable no-underscore-dangle */

    /* eslint-disable no-underscore-dangle */
    disableEditHandler() {
        if (this.editHandler && this.editHandler._enabled) {
            this.editHandler.disable();
        }
    }
    /* eslint-enable no-underscore-dangle */

    addEmptyAnnotation(shape) {
        let labelFromFilter = this.filterLabel.name === 'All' ? '' : this.filterLabel.name;
        const label = this.annotationTemplate ?
              this.annotationTemplate.properties.label : labelFromFilter;
        const description = this.annotationTemplate ?
              this.annotationTemplate.properties.description : '';

        let annotationCollection = wrapFeatureCollection(
            propertiesToAnnotationFeature({
                geometry: shape.geometry,
                label,
                description
            })
        );
        this.createAnnotations(annotationCollection, !this.annotationTemplate, false);
    }

    onShapeCreated(shapeLayer) {
        this.disableToolbarAction = true;
        let shapeJson = shapeLayer.toGeoJSON();
        this.addEmptyAnnotation(shapeJson);
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
                    if (!this.sidebarDisabled && !this.clickedId) {
                        this.hoveredId = feature.id;
                        this.setLayerStyle(e.target, RED, 'annotate-hover-marker');
                        this.$anchorScroll('anchor' + this.hoveredId.toString());
                        this.$scope.$evalAsync();
                    }
                }).on('mouseout', (e) => {
                    if (!this.sidebarDisabled && !this.clickedId) {
                        delete this.hoveredId;
                        this.setLayerStyle(e.target, BLUE, 'annotate-marker');
                        this.$scope.$evalAsync();
                    }
                }).on('click', (e) => {
                    this.getMap().then((mapWrapper) => {
                        mapWrapper.map.panTo(e.target.getBounds().getCenter());
                        if (!this.sidebarDisabled) {
                            delete this.hoveredId;
                            if (this.clickedId !== feature.id) {
                                let prevLayer = Object
                                    .values(mapWrapper.getLayers('Annotation')[0]._layers)
                                    .filter((l) => l.feature.id === this.clickedId);
                                if (prevLayer.length) {
                                    this.setLayerStyle(prevLayer[0], BLUE, 'annotate-marker');
                                }
                                this.clickedId = feature.id;
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

    disableTransformHandler(mapWrapper) {
        if (!_.isEmpty(mapWrapper.getLayers('draw')[0])
            && mapWrapper.getLayers('draw')[0].transform) {
            mapWrapper.getLayers('draw')[0].transform.disable();
        }
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

    doPanToAnnotation(annotation) {
        if (!this.sidebarDisabled) {
            this.getMap().then(mapWrapper => {
                let panTo = annotation;
                if (annotation.geometry.type === 'Polygon') {
                    panTo = turfCenter(annotation);
                }
                mapWrapper.map.panTo([
                    panTo.geometry.coordinates[1],
                    panTo.geometry.coordinates[0]
                ]);
            });
        }
    }

    bindHotkeys() {
        let bindings = [
            {
                combo: 'n',
                description: 'Create new rectangle',
                callback: () => {
                    if (!this.disableToolbarAction) {
                        if (angular.element('.btn-anno-rectangle')) {
                            this.$timeout(() => angular.element('.btn-anno-rectangle').click());
                        }
                    }
                }
            },
            {
                combo: 'p',
                description: 'Create new polygon',
                callback: () => {
                    if (!this.sidebarDisabled) {
                        if (angular.element('.btn-anno-polygon')) {
                            this.$timeout(() => angular.element('.btn-anno-polygon').click());
                        }
                    }
                }
            },
            {
                combo: 'm',
                description: 'Create new point',
                callback: () => {
                    if (!this.disableToolbarAction) {
                        if (angular.element('.btn-anno-point')) {
                            this.$timeout(() => angular.element('.btn-anno-point').click());
                        }
                    }
                }
            },
            {
                combo: 'c',
                description: 'Clone annotation',
                callback: () => {
                    if (this.clickedId && !this.sidebarDisabled) {
                        let clickedAnnotation = this.annotations.get(this.clickedId);
                        this.createAnnotations(wrapFeatureCollection(clickedAnnotation), true);
                    }
                }
            },
            {
                combo: 'e',
                description: 'Edit annotation',
                callback: () => {
                    if (this.clickedId && !this.sidebarDisabled) {
                        this.editAnnotation(this.clickedId);
                    }
                }
            },
            {
                combo: 'd',
                description: 'Delete annotation',
                callback: () => {
                    if (this.clickedId && !this.sidebarDisabled) {
                        let answer = this.$window.confirm('Delete this annotation?');
                        if (answer) {
                            this.deleteAnnotation(this.clickedId);
                        }
                    }
                }
            },
            {
                combo: 'r',
                description: 'Rotate/rescale annotation shape',
                callback: () => {
                    this.transformDrawlayer('rotate');
                }
            },
            {
                combo: 'up',
                description: 'Move annotation north',
                callback: () => {
                    this.transformDrawlayer('translate', {direction: 'up'});
                }
            },
            {
                combo: 'down',
                description: 'Move annotation south',
                callback: () => {
                    this.transformDrawlayer('translate', {direction: 'down'});
                }
            },
            {
                combo: 'left',
                description: 'Move annotation west',
                callback: () => {
                    this.transformDrawlayer('translate', {direction: 'left'});
                }
            },
            {
                combo: 'right',
                description: 'Move annotation east',
                callback: () => {
                    this.transformDrawlayer('translate', {direction: 'right'});
                }
            },
            {
                combo: 'shift+return',
                allowIn: ['INPUT', 'TEXTAREA'],
                description: 'Submit annotation',
                callback: () => {
                    if (angular.element('.annotation-confirm')) {
                        this.$timeout(() => angular.element('.annotation-confirm').click());
                    }
                }
            },
            {
                combo: 'esc',
                allowIn: ['INPUT', 'TEXTAREA'],
                description: 'Cancel submitting annotation',
                callback: () => {
                    if (angular.element('.annotation-cancel')) {
                        this.$timeout(() => angular.element('.annotation-cancel').click());
                    }
                    if (!this.sidebarDisabled) {
                        if (angular.element('.btn-anno-cancel')) {
                            this.$timeout(() => angular.element('.btn-anno-cancel').click());
                        }
                    }
                }
            }
        ];
        bindings.forEach(binding => this.hotkeys.add(binding));
        this.hotkeys.bindTo(this.$scope);
    }

}

const AnnotateModule = angular.module('pages.projects.edit.annotate', ['cfp.hotkeys']);

AnnotateModule.controller(
    'AnnotateController', AnnotateController
);

export default AnnotateModule;
