import {Map} from 'immutable';
import typeToReducer from 'type-to-reducer';
import {
    WORKSPACE_LOAD, WORKSPACE_UPDATE_NAME, WORKSPACE_FETCH, WORKSPACE_SET_OPTIONS
} from '../actions/workspace-actions';

export const workspaceReducer = typeToReducer({
    [WORKSPACE_LOAD]: (state, action) => {
        return Object.assign({}, state, {
            lastWorkspaceSave: new Date(),
            analysisSaves: new Map(),
            analysisRefreshes: new Map(),
            workspace: action.payload,

            nodes: new Map(),
            previewNodes: [],
            selectingNode: null,
            selectedNode: null,
            createNode: null,
            linkNode: null,

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
            const workspace = action.payload.data;
            let createNode = null;
            if (!workspace.analyses.length) {
                createNode = 'select';
            }
            return Object.assign({}, state, {
                fetching: false,
                workspace,
                createNode,
                readonly: false,
                lastWorkspaceSave: new Date(),
                histograms: new Map(),
                errors: state.errors.delete('http')
            });
        }
    },
    [WORKSPACE_SET_OPTIONS]: (state, action) => {
        let payload = action.payload;
        let options = {
            readonly: payload.hasOwnProperty('readonly') ? payload.readonly : false,
            controls: payload.hasOwnProperty('controls') ? payload.controls : true
        };
        return Object.assign({}, state, options);
    }
});
