import typeToReducer from 'type-to-reducer';

import {
    NODE_PREVIEWS, NODE_SET_ERROR, NODE_UPDATE_SOFT, NODE_UPDATE_HARD, NODE_INIT
} from '../actions/node-actions';

export const nodeReducer = typeToReducer({
    [NODE_INIT]: (state, action) => {
        return Object.assign({}, state, {nodes: action.payload});
    },
    [NODE_PREVIEWS]: {
        START_COMPARE: (state, action) => {
            return Object.assign({}, state, {selectedNode: action.nodeId});
        },
        FINISH_COMPARE: (state, action) => {
            return Object.assign({}, state, {
                previewNodes: [state.selectedNode, action.nodeId],
                showMap: true,
                selectedNode: null
            });
        },
        CANCEL_COMPARE: (state) => {
            return Object.assign({}, state, {selectedNode: null});
        },
        SINGLE: (state, action) => {
            return Object.assign({}, state, {showMap: true, previewNodes: [action.payload]});
        }
    },
    [NODE_SET_ERROR]: (state, action) => {
        if (action.payload) {
            return Object.assign({}, state, {
                toolErrors: state.toolErrors.set(action.nodeId, action.payload)
            });
        }
        return Object.assign({}, state, {
            toolErrors: state.toolErrors.delete(action.nodeId)
        });
    },
    [NODE_UPDATE_HARD]: {
        PENDING: (state) => {
            return state;
        },
        REJECTED: (state) => {
            return state;
        },
        FULFILLED: (state, action) => {
            return Object.assign({}, state, {
                previewNodes: state.previewNodes.concat([]),
                tool: action.meta.tool,
                nodes: state.nodes.set(action.meta.node.id, action.meta.node),
                lastToolSave: new Date(),
                lastToolRefresh: new Date()
            });
        }
    },
    [NODE_UPDATE_SOFT]: {
        PENDING: (state) => {
            return state;
        },
        REJECTED: (state) => {
            return state;
        },
        FULFILLED: (state, action) => {
            return Object.assign({}, state, {
                lastToolSave: new Date(),
                nodes: state.nodes.set(action.meta.node.id, action.meta.node),
                tool: action.meta.tool
            });
        }
    }
});
