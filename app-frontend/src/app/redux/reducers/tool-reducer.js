import {Map} from 'immutable';
import typeToReducer from 'type-to-reducer';
import {
    TOOL_LOAD, TOOL_UPDATE_NAME, TOOL_FETCH
} from '../actions/lab-actions';

export const toolReducer = typeToReducer({
    [TOOL_LOAD]: (state, action) => {
        return Object.assign({}, state, {
            tool: action.payload, lastToolSave: new Date(),
            readonly: action.readonly,
            previewNodes: [], selectedNode: null, nodes: new Map(),
            histograms: new Map()
        });
    },
    [TOOL_UPDATE_NAME]: {
        PENDING: (state, action) => {
            return Object.assign(
                {}, state,
                {
                    tool: action.meta.tool,
                    updating: action.forceUpdate ? action.payload.id : false
                }
            );
        },
        REJECTED: (state, action) => {
            return Object.assign({}, state, {tool: action.meta.oldTool, toolError: action.payload});
        },
        FULFILLED: (state, action) => {
            return Object.assign(
                {}, state, {
                    tool: action.meta.tool,
                    lastToolSave: new Date()
                }
            );
        }
    },
    [TOOL_FETCH]: {
        PENDING: (state, action) => {
            return Object.assign({}, state, {
                fetching: action.toolId, toolErrors: state.toolErrors.delete('http')
            });
        },
        REJECTED: (state, action) => {
            return Object.assign({}, state, {
                fetching: false, toolErrors: state.toolErrors.set('http', action.payload)
            });
        },
        FULFILLED: (state, action) => {
            return Object.assign({}, state, {
                fetching: false,
                tool: action.payload.data,
                readonly: false,
                lastToolSave: new Date(),
                histograms: new Map(),
                toolErrors: state.toolErrors.delete('http')
            });
        }
    }
});
