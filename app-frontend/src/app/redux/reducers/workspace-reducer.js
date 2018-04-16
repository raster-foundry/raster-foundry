import {Map} from 'immutable';
import typeToReducer from 'type-to-reducer';
import {
    WORKSPACE_SET, WORKSPACE_SET_DIAGRAM, WORKSPACE_UPDATE_NAME,
    WORKSPACE_FETCH, WORKSPACE_SET_OPTIONS
} from '../actions/workspace-actions';

export const workspaceReducer = typeToReducer({
    [WORKSPACE_SET]: (state, action) => {
        return Object.assign({}, state, {
            workspace: action.payload
        });
    },
    [WORKSPACE_SET_DIAGRAM]: (state, action) => {
        let analysisRoots = new Map();
        let workspace = state.workspace;
        workspace.analyses.forEach((a) => {
            analysisRoots = analysisRoots.set(a.id, a.executionParameters.id);
        });
        let createNode = null;
        if (!workspace.analyses.length) {
            createNode = 'select';
        }
        return Object.assign({}, state, {
            analysisRoots,

            nodes: action.payload.nodes,
            linksBySource: action.payload.linksBySource,
            linksByTarget: action.payload.linksByTarget,
            previewNodes: [],
            selectingNode: null,
            selectedNode: null,
            linkNode: null,
            createNode,
            graph: action.payload.graph,
            onShapeMove: action.payload.onShapeMove,

            histograms: new Map(),
            statistics: new Map()
        });
    },
    [WORKSPACE_UPDATE_NAME]: {
        PENDING: (state, action) => {
            return Object.assign(
                {}, state,
                {
                    workspace: action.meta.workspace,
                    updating: action.forceUpdate ? action.payload.id : false
                }
            );
        },
        REJECTED: (state, action) => {
            return Object.assign({}, state, {
                workspace: action.meta.oldWorkspace,
                errors: state.errors.set('http', action.payload)
            });
        },
        FULFILLED: (state, action) => {
            return Object.assign(
                {}, state, {
                    workspace: action.meta.workspace,
                    lastWorkspaceSave: new Date()
                }
            );
        }
    },
    [WORKSPACE_FETCH]: {
        PENDING: (state, action) => {
            return Object.assign({}, state, {
                fetching: action.workspaceId, errors: state.errors.delete('http')
            });
        },
        REJECTED: (state, action) => {
            return Object.assign({}, state, {
                fetching: false, errors: state.errors.set('http', action.payload)
            });
        },
        FULFILLED: (state, action) => {
            return Object.assign({}, state, {
                fetching: false,
                workspace: action.payload.data,
                errors: state.errors.delete('http')
            });
        }
    },
    [WORKSPACE_SET_OPTIONS]: (state, action) => {
        let payload = action.payload;
        let options = {
            readonly: payload.readonly,
            controls: payload.controls,
            cellDimensions: payload.cellDimensions,
            nodeSeparationFactor: payload.nodeSeparationFactor
        };
        return Object.assign({}, state, options);
    }
});
