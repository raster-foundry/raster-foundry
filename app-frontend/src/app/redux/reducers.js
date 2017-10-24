import _ from 'lodash';
import {Map} from 'immutable';
import { API_INIT } from './actions/api-actions';
import { TOOL_ACTION_PREFIX } from './actions/lab-actions';
import { NODE_ACTION_PREFIX } from './actions/node-actions';
import { HISTOGRAM_ACTION_PREFIX } from './actions/histogram-actions';
import { STATISTICS_ACTION_PREFIX } from './actions/statistics-actions';

import { toolReducer } from './reducers/tool-reducer';
import { nodeReducer } from './reducers/node-reducer';
import { histogramReducer } from './reducers/histogram-reducer';
import { statisticsReducer } from './reducers/statistics-reducer';

const INITIAL_LAB_STATE = {
    lastToolSave: null, lastToolRefresh: null, tool: null, toolErrors: new Map(),
    readonly: null,
    nodes: new Map(), previewNodes: [], selectedNode: null,
    histograms: new Map(), statistics: new Map(),
    updating: false, fetching: false, error: null
};

function lab(state = INITIAL_LAB_STATE, action) {
    if (!action || !action.type) {
        return state;
    }

    const prefix = _.first(action.type.split('_'));

    switch (prefix) {
    case TOOL_ACTION_PREFIX:
        return toolReducer(state, action);
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

const INITIAL_API_STATE = {
    apiToken: null,
    apiUrl: null,
    tileUrl: null
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
            tileUrl: action.payload.tileUrl
        });
    default:
        return state;
    }
}

export default {
    lab, api
};
