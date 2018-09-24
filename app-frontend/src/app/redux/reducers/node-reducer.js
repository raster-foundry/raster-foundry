import typeToReducer from 'type-to-reducer';
import {Map} from 'immutable';
import _ from 'lodash';

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
                    preventSelecting: false,
                    selectingNode: 'select',
                    selectedNode: action.nodeId
                });
            } else if (state.selectingNode === 'select') {
                return Object.assign({}, state, {
                    preventSelecting: false,
                    selectingNode: null,
                    selectedNode: null,
                    showMap: true,
                    previewNodes: [state.selectedNode, action.nodeId]
                });
            }
            return Object.assign({}, state, {
                preventSelecting: false,
                selectingNode: null,
                selectedNode: null,
                showMap: true,
                previewNodes: [action.nodeId]
            });
        },
        COMPARE_NODES: (state, action) => {
            return Object.assign({}, state, {
                preventSelecting: false,
                selectingNodes: null,
                selectedNode: null,
                showMap: true,
                previewNodes: [...action.nodes]
            });
        },
        CANCEL_SELECT: (state) => {
            return Object.assign({}, state, {
                preventSelecting: false,
                selectingNode: null,
                selectedNode: null
            });
        },
        PAUSE_SELECT: (state) => {
            return Object.assign({}, state, {
                preventSelecting: true
            });
        },
        ERROR: (state, action) => {
            return Object.assign({}, state, {
                preventSelecting: false,
                selectingNode: null,
                selectedNode: null,
                selectingError: action.error
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
            if (action.meta.nodes) {
                const nodeMap = _.reduce(
                    action.meta.nodes,
                    (m, node) => m.set(node.id, node),
                    new Map()
                );
                return Object.assign({}, state, {
                    previewNodes: state.previewNodes.concat([]),
                    analysis: action.meta.analysis,
                    nodes: _.merge({}, state.nodes, nodeMap),
                    lastAnalysisSave: new Date(),
                    lastAnalysisRefresh: new Date()
                });
            } else if (action.meta.node) {
                return Object.assign({}, state, {
                    previewNodes: state.previewNodes.concat([]),
                    analysis: action.meta.analysis,
                    nodes: state.nodes.set(action.meta.node.id, action.meta.node),
                    lastAnalysisSave: new Date(),
                    lastAnalysisRefresh: new Date()
                });
            }
            throw new Error('Node update needs to specify node or nodes in action meta');
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
