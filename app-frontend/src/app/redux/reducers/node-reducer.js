import typeToReducer from 'type-to-reducer';

import {
    NODE_PREVIEWS, NODE_SET_ERROR, NODE_UPDATE_SOFT, NODE_UPDATE_HARD, NODE_INIT
} from '../actions/node-actions';

export const nodeReducer = typeToReducer({
    [NODE_INIT]: (state, action) => {
        return Object.assign({}, state, {nodes: action.payload});
    },
    [NODE_PREVIEWS]: {
        START_PREVIEW: (state) => {
            return Object.assign({}, state, {
                selectingNode: 'preview',
                selectedNode: null
            });
        },
        START_COMPARE: (state) => {
            return Object.assign({}, state, {
                selectingNode: 'compare',
                selectedNode: null
            });
        },
        SELECT_NODE: (state, action) => {
            if (state.selectingNode === 'compare') {
                return Object.assign({}, state, {
                    selectingNode: 'select',
                    selectedNode: action.nodeId
                });
            } else if (state.selectingNode === 'select') {
                return Object.assign({}, state, {
                    selectingNode: null,
                    selectedNode: null,
                    showMap: true,
                    previewNodes: [state.selectedNode, action.nodeId]
                });
            }
            return Object.assign({}, state, {
                selectingNode: null,
                selectedNode: null,
                showMap: true,
                previewNodes: [action.nodeId]
            });
        },
        COMPARE_NODES: (state, action) => {
            return Object.assign({}, state, {
                selectingNodes: null,
                selectedNode: null,
                showMap: true,
                previewNodes: [...action.nodes]
            });
        },
        CANCEL_SELECT: (state) => {
            return Object.assign({}, state, {
                selectingNode: null,
                selectedNode: null
            });
        }
    },
    [NODE_SET_ERROR]: (state, action) => {
        if (action.payload) {
            return Object.assign({}, state, {
                analysisErrors: state.analysisErrors.set(action.nodeId, action.payload)
            });
        }
        return Object.assign({}, state, {
            analysisErrors: state.analysisErrors.delete(action.nodeId)
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
                analysis: action.meta.analysis,
                nodes: state.nodes.set(action.meta.node.id, action.meta.node),
                lastAnalysisSave: new Date(),
                lastAnalysisRefresh: new Date()
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
                lastAnalysisSave: new Date(),
                nodes: state.nodes.set(action.meta.node.id, action.meta.node),
                analysis: action.meta.analysis
            });
        }
    }
});
