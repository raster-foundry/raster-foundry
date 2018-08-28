import typeToReducer from 'type-to-reducer';
import {OrderedMap, OrderedSet} from 'immutable';
import _ from 'lodash';

import {
    ANNOTATIONS_RESET, ANNOTATIONS_SIDEBAR,
    ANNOTATIONS_FETCH, ANNOTATIONS_LABELS_FETCH,
    ANNOTATIONS_CREATE, ANNOTATIONS_UPDATE, ANNOTATIONS_FILTER,
    ANNOTATIONS_CLEAR, ANNOTATIONS_EDIT, ANNOTATIONS_DELETE,
    ANNOTATIONS_BULK_CREATE,
    ANNOTATIONS_TRANSFORM_DRAWLAYER,
    ANNOTATIONS_UPLOAD_SHAPEFILE,
    ANNOTATIONS_IMPORT_SHAPEFILE,
    ANNOTATIONS_UPLOAD_SHAPEFILE_DELETE,

    fetchAnnotations, updateAnnotation, fetchLabels
} from '_redux/actions/annotation-actions';

import {
    PROJECT_EDIT_LAYER
} from '_redux/actions/project-actions';

export const annotationReducer = typeToReducer({
    [ANNOTATIONS_RESET]: (state) => {
        return Object.assign({}, state, {
            projectId: null, projectMap: null, editHandler: null,

            annotations: new OrderedMap(), editingAnnotation: null,
            fetchingAnnotations: false, fetchingAnnotationsError: null,
            sidebarDisabled: false, annotationTemplate: null,

            labels: [], filter: 'All'
        });
    },
    [ANNOTATIONS_SIDEBAR]: {
        ENABLE: (state) => {
            return Object.assign({}, state, {
                sidebarDisabled: false
            });
        },
        DISABLE: (state) => {
            return Object.assign({}, state, {
                sidebarDisabled: true
            });
        }
    },
    // action.payload should contain the returned annotations (in the fulfilled case)
    // not clear what comes back in pending
    // error body in rejected.action.payload
    [ANNOTATIONS_UPLOAD_SHAPEFILE]: {
        PENDING: (state) => {
            return Object.assign({}, state, {
                fetchingAnnotations: true,
                uploadAnnotationsError: null
            });
        },
        REJECTED: (state, action) => {
            return Object.assign(
                {}, state, {uploadAnnotationsError: action.payload.response.data}
            );
        },
        FULFILLED: (state, action) => {
            let annotationShapefileProps = action.payload.data;
            return Object.assign({}, state, {
                annotationShapefileProps,
                fetchingAnnotations: false,
                uploadAnnotationsError: null
            });
        }
    },
    [ANNOTATIONS_IMPORT_SHAPEFILE]: {
        PENDING: (state) => {
            return Object.assign({}, state, {
                fetchingAnnotations: true,
                uploadAnnotationsError: null
            });
        },
        REJECTED: (state, action) => {
            return Object.assign(
                {}, state, {uploadAnnotationsError: action.payload.response.data}
            );
        },
        FULFILLED: (state, action) => {
            let annotations = state.annotations;
            let newAnnotations = action.payload.data;
            newAnnotations.forEach(annotation => {
                annotations = annotations.set(annotation.id, annotation);
            });
            return Object.assign({}, state, {
                annotations,
                fetchingAnnotations: false,
                annotationShapefileProps: [],
                uploadAnnotationsError: null
            });
        }
    },
    [ANNOTATIONS_UPLOAD_SHAPEFILE_DELETE]: (state) => {
        return Object.assign({}, state, {annotationShapefileProps: []});
    },
    [ANNOTATIONS_FETCH]: {
        PENDING: (state) => {
            return Object.assign({}, state, {
                fetchingAnnotations: true,
                fetchingAnnotationsError: null
            });
        },
        REJECTED: (state, action) => {
            return Object.assign({}, state, {
                fetchingAnnotations: false,
                fetchingAnnotationsError: action.payload
            });
        },
        FULFILLED: (state, action) => {
            let annotations = state.annotations;
            let data = action.payload.data;
            if (data.hasNext) {
                fetchAnnotations(data.page + 1)(action.asyncDispatch, () => action.meta.state);
            }
            data.features.forEach(annotation => {
                annotations = annotations.set(annotation.id, annotation);
            });

            return Object.assign({}, state, {
                annotations,
                fetchingAnnotations: data.hasNext,
                fetchingAnnotationsError: null
            });
        }
    },
    [ANNOTATIONS_LABELS_FETCH]: {
        PENDING: (state) => {
            return state;
        },
        REJECTED: (state, action) => {
            return Object.assign({}, state, {fetchingAnnotationsError: action.payload});
        },
        FULFILLED: (state, action) => {
            let filters = action.payload.data;
            let unlabeledFilter = filters.indexOf('');
            if (unlabeledFilter > -1) {
                filters[unlabeledFilter] = 'Unlabeled';
            }
            return Object.assign({}, state, {labels: action.payload.data});
        }
    },
    [ANNOTATIONS_CREATE]: {
        PENDING: (state) => {
            return state;
        },
        REJECTED: (state, action) => {
            // eslint-disable-next-line
            console.error('Error creating annotations', action.payload);
            return state;
        },
        FULFILLED: (state, action) => {
            let newAnnotations = action.payload.data.features;
            let annotations = state.annotations;
            let labels = new OrderedSet(state.labels);
            newAnnotations.forEach((annotation) => {
                annotations = annotations.set(annotation.id, annotation);
                labels = labels.add(annotation.properties && annotation.properties.label ?
                                    annotation.properties.label : 'Unlabeled');
            });
            if (newAnnotations.length === 1 && action.meta.edit) {
                action.asyncDispatch({
                    type: `${ANNOTATIONS_EDIT}_START`,
                    payload: newAnnotations[0].id,
                    meta: action.meta
                });
            }
            return Object.assign(
                {},
                state,
                {
                    annotations,
                    labels: labels.toArray()
                }
            );
        }
    },
    [ANNOTATIONS_UPDATE]: {
        PENDING: (state) => {
            return state;
        },
        REJECTED: (state, action) => {
            //eslint-disable-next-line
            console.error('Update Annotation Failed', action);
            // TODO revert state change and show network error
            return state;
        },
        FULFILLED: (state, action) => {
            fetchLabels()(action.asyncDispatch, () => action.meta.state);
            let annotation = action.meta.annotation;
            return Object.assign({}, state, {
                annotations: state.annotations.set(annotation.id, annotation),
                editingAnnotation: null,
                sidebarDisabled: false
            });
        }
    },
    [ANNOTATIONS_CLEAR]: {
        PENDING: (state) => {
            return state;
        },
        REJECTED: (state) => {
            return state;
        },
        FULFILLED: (state) => {
            return Object.assign({}, state, {
                annotations: new OrderedMap(),
                labels: []
            });
        }
    },
    [ANNOTATIONS_DELETE]: {
        PENDING: (state) => {
            return state;
        },
        REJECTED: (state) => {
            return state;
        },
        FULFILLED: (state, action) => {
            fetchLabels()(action.asyncDispatch, () => action.meta.state);
            // delete clicked highlight
            state.projectMap.deleteLayers('highlight');
            return Object.assign({}, state, {
                annotations: state.annotations.delete(action.meta.annotationId)
            });
        }
    },
    [ANNOTATIONS_FILTER]: (state, action) => {
        return Object.assign({}, state, {filter: action.payload});
    },
    [ANNOTATIONS_EDIT]: {
        START: (state, action) => {
            let mapWrapper = state.projectMap;
            let editingAnnotation = action.payload;

            // remove clicked highlight
            mapWrapper.deleteLayers('highlight');
            // disable transform handler
            let drawLayer = _.first(mapWrapper.getLayers('draw'));
            if (!_.isEmpty(drawLayer)
                && drawLayer.transform) {
                drawLayer.transform.disable();
            }

            // create draw layer
            let annotation = state.annotations.get(editingAnnotation);
            action.asyncDispatch({
                type: `${PROJECT_EDIT_LAYER}_START`,
                payload: {
                    geometry: annotation.geometry,
                    options: {},
                    meta: action.meta
                }
            });

            return Object.assign({}, state, {
                editingAnnotation,
                sidebarDisabled: !!action.payload
            });
        },
        CANCEL: (state, action) => {
            action.asyncDispatch({
                type: `${PROJECT_EDIT_LAYER}_FINISH`
            });
            return Object.assign({}, state, {
                editingAnnotation: null,
                sidebarDisabled: false
            });
        },
        FINISH: (state, action) => {
            let mapWrapper = state.projectMap;
            let drawLayer = _.first(mapWrapper.getLayers('draw'));
            if (drawLayer.transform) {
                drawLayer.transform.disable();
            }

            let {geometry} = drawLayer.toGeoJSON();
            let updatedAnnotation = Object.assign(
                {},
                action.payload,
                {
                    geometry
                }
            );
            action.asyncDispatch({
                type: `${PROJECT_EDIT_LAYER}_FINISH`
            });
            updateAnnotation(updatedAnnotation)(action.asyncDispatch, () => action.meta.state);
            fetchLabels()(action.asyncDispatch, () => action.meta.state);

            return Object.assign({}, state, {
                editingAnnotation: null,
                sidebarDisabled: false
            });
        }
    },
    [ANNOTATIONS_BULK_CREATE]: {
        START: (state, action) => {
            return Object.assign({}, state, {
                annotationTemplate: action.payload,
                sidebarDisabled: true
            });
        },
        FINISH: (state) => {
            return Object.assign({}, state, {
                annotationTemplate: null,
                sidebarDisabled: false
            });
        }
    },
    [ANNOTATIONS_TRANSFORM_DRAWLAYER]: (state, action) => {
        let {transform, options} = action.payload;
        let drawLayer = _.first(state.projectMap.getLayers('draw'));
        let drawnGeojson = drawLayer && drawLayer.toGeoJSON();
        if (!drawnGeojson || drawnGeojson.geometry && drawnGeojson.geometry.type !== 'Polygon') {
            return state;
        }
        state.projectMap.deleteLayers('highlight');

        if (!_.isEmpty(drawLayer)
            && drawLayer.transform) {
            drawLayer.transform.disable();
        }

        switch (transform) {
        case 'rotate':
            if (!_.isEmpty(drawLayer)
                && drawLayer.transform) {
                action.asyncDispatch({
                    type: `${PROJECT_EDIT_LAYER}_FINISH`
                });
                action.asyncDispatch({
                    type: `${PROJECT_EDIT_LAYER}_START`,
                    payload: {
                        geometry: drawnGeojson.geometry,
                        options: {}
                    }
                });
            } else {
                let coordinates = _.map(drawnGeojson.geometry.coordinates[0], (c) => {
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
                state.projectMap.setLayer('draw', polygonLayer, false);
                polygonLayer.transform.enable({rotation: true, scaling: true});
                state.projectMap.map.panTo(polygonLayer.getCenter());
            }
            return state;
        case 'translate':
            let coordinates =
                _.map(drawnGeojson.geometry.coordinates[0], (c) => {
                    let point = state.projectMap.map.latLngToContainerPoint(
                        L.latLng(c[1], c[0])
                    );
                    switch (options.direction) {
                    case 'up':
                        point.y -= 5;
                        break;
                    case 'down':
                        point.y += 5;
                        break;
                    case 'left':
                        point.x -= 5;
                        break;
                    case 'right':
                        point.x += 5;
                        break;
                    default:
                    }
                    let coors = state.projectMap.map.containerPointToLatLng(point);
                    return [coors.lng, coors.lat];
                });
            drawnGeojson.geometry.coordinates = [coordinates];
            action.asyncDispatch({
                type: `${PROJECT_EDIT_LAYER}_FINISH`
            });
            action.asyncDispatch({
                type: `${PROJECT_EDIT_LAYER}_START`,
                payload: {
                    geometry: drawnGeojson.geometry,
                    options: {}
                }
            });
            return state;
        default:
            // eslint-disable-next-line
            console.error(`Unsupported draw layer transform: ${transform}`);
            return state;
        }
    }
});
