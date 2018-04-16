import _ from 'lodash';
import {OrderedMap, Map, Set} from 'immutable';
import { API_INIT } from './actions/api-actions';

// import { ANALYSIS_ACTION_PREFIX } from './actions/analysis-actions';
import { WORKSPACE_ACTION_PREFIX } from './actions/workspace-actions';
import { NODE_ACTION_PREFIX } from './actions/node-actions';
import { HISTOGRAM_ACTION_PREFIX } from './actions/histogram-actions';
import { STATISTICS_ACTION_PREFIX } from './actions/statistics-actions';
import { PROJECT_ACTION_PREFIX } from './actions/project-actions';
import { SHAPES_ACTION_PREFIX } from './actions/shape-actions';

import { ANNOTATIONS_ACTION_PREFIX } from './actions/annotation-actions';

// import { analysisReducer } from './reducers/analysis-reducer';
import { workspaceReducer } from './reducers/workspace-reducer';
import { nodeReducer } from './reducers/node-reducer';
import { histogramReducer } from './reducers/histogram-reducer';
import { statisticsReducer } from './reducers/statistics-reducer';

import { annotationReducer } from './reducers/annotation-reducer';
import { projectReducer} from './reducers/project-reducer';
import { shapeReducer} from './reducers/shape-reducer';

const INITIAL_LAB_STATE = {
    // analysis state
    workspace: null,
    errors: new Map(),
    updating: false,
    fetching: false,
    error: null,
    readonly: false,
    controls: true,
    nodeSeparationFactor: 0.8,
    cellDimensions: { width: 400, height: 200 },
    graph: null,
    onShapeMove: () => {
        throw new Error('onShapeMove was called before being set');
    },

    // node state
    nodes: new Map(),
    linksBySource: new Map(),
    linksByTarget: new Map(),
    analysisRoots: new Map(),
    previewNodes: [], selectingNode: null, selectedNode: null,
    createNode: null, createNodeSelection: null, linkNode: null,

    // histogram & statistics state
    histograms: new Map(), statistics: new Map()
};

function lab(state = INITIAL_LAB_STATE, action) {
    if (!action || !action.type) {
        return state;
    }

    const prefix = _.first(action.type.split('_'));

    switch (prefix) {
    case WORKSPACE_ACTION_PREFIX:
        return workspaceReducer(state, action);
    // case ANALYSIS_ACTION_PREFIX:
    //     return analysisReducer(state, action);
    case NODE_ACTION_PREFIX:
        return nodeReducer(state, action);
    case HISTOGRAM_ACTION_PREFIX:
        return histogramReducer(state, action);
    case STATISTICS_ACTION_PREFIX:
        return statisticsReducer(state, action);
    default:
        return state;
    }
}

const INITIAL_PROJECTS_STATE = {
    projectId: null, projectMap: null, editHandler: null,

    annotations: new OrderedMap(), editingAnnotation: null,
    fetchingAnnotations: false, fetchingAnnotationsError: null,
    sidebarDisabled: false, annotationTemplate: null,

    labels: [], filter: 'All'
};

function projects(state = INITIAL_PROJECTS_STATE, action) {
    if (!action || !action.type) {
        return state;
    }

    const prefix = _.first(action.type.split('_'));

    switch (prefix) {
    case ANNOTATIONS_ACTION_PREFIX:
        return annotationReducer(state, action);
    case PROJECT_ACTION_PREFIX:
        return projectReducer(state, action);
    default: return state;
    }
}

const INITIAL_SHAPE_STATE = {
    mapId: null,
    resolve: null,
    reject: null,
    resolveEdit: null,
    rejectEdit: null
};

function shape(state = INITIAL_SHAPE_STATE, action) {
    if (!action || !action.type) {
        return state;
    }
    const prefix = _.first(action.type.split('_'));

    switch (prefix) {
    case SHAPES_ACTION_PREFIX:
        return shapeReducer(state, action);
    default:
        return state;
    }
}

const INITIAL_API_STATE = {
    apiToken: null, apiUrl: null, tileUrl: null,
    user: null
};

function api(state = INITIAL_API_STATE, action) {
    if (!action || !action.type) {
        return state;
    }
    switch (action.type) {
    case API_INIT:
        return Object.assign({}, state, {
            apiToken: action.payload.apiToken,
            apiUrl: action.payload.apiUrl,
            tileUrl: action.payload.tileUrl,
            user: action.payload.user
        });
    default:
        return state;
    }
}

export default {
    lab, projects, shape, api
};
