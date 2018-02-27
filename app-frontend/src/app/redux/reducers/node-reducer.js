import typeToReducer from 'type-to-reducer';
import _ from 'lodash';

import {
    NODE_PREVIEWS, NODE_SET_ERROR, NODE_UPDATE_SOFT, NODE_UPDATE_HARD, NODE_SET,
    NODE_CREATE, NODE_LINK
} from '../actions/node-actions';

export const nodeReducer = typeToReducer({
    [NODE_SET]: (state, action) => {
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
                errors: state.errors.set(action.nodeId, action.payload)
            });
        }
        return Object.assign({}, state, {
            errors: state.errors.delete(action.nodeId)
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
            let workspace = _.clone(state.workspace);
            let analysis = action.meta.analysis;
            let analysisIndex = _.findIndex(workspace.analyses, (a) => a.id === analysis.id);
            workspace.analyses[
                analysisIndex >= 0 ? analysisIndex : workspace.analyses.length
            ] = analysis;

            if (action.meta.deleteAnalysis) {
                let deleteAnalysisId = action.meta.deleteAnalysis;
                _.remove(workspace.analyses, (a) => a.id === deleteAnalysisId);
            }

            let nodes = state.nodes.merge(action.meta.nodes);
            if (action.meta.deleteNodes && action.meta.deleteNodes.length) {
                action.meta.deleteNodes.forEach((id) => {
                    nodes = nodes.delete(id);
                });
            }

            return Object.assign({}, state, {
                previewNodes: state.previewNodes.concat([]),
                workspace,
                nodes,
                analysisSaves: state.analysisSaves.set(action.meta.analysis.id, new Date()),
                analysisRefreshes: state.analysisSaves.set(action.meta.analysis.id, new Date())
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
            let workspace = _.clone(state.workspace);
            let analysis = action.meta.analysis;
            let analysisIndex = _.findIndex(workspace.analyses, (a) => a.id === analysis.id);
            workspace.analyses[
                analysisIndex >= 0 ? analysisIndex : workspace.analyses.length
            ] = analysis;

            return Object.assign({}, state, {
                workspace,
                nodes: state.nodes.merge(action.meta.nodes),
                analysisSaves: state.analysisSaves.set(action.meta.analysis.id, new Date())
            });
        }
    },
    [NODE_CREATE]: {
        START: (state, action) => {
            return Object.assign({}, state, {
                createNode: action.payload.nodeType ? action.payload.nodeType : 'select'
            });
        },
        SELECT: (state, action) => {
            return Object.assign({}, state, {
                createNodeSelection: action.payload
            });
        },
        FINISH: {
            PENDING: (state) => {
                return state;
            },
            REJECTED: (state) => {
                return Object.assign({}, state, {createNodeSelection: null});
            },
            FULFILLED: (state, action) => {
                let updated = _.clone(state.workspace);

                const analysis = Object.assign({}, action.payload.data, {
                    addLocation: action.meta.coordinates
                });

                updated.analyses.push(analysis);

                return Object.assign({}, state, {
                    workspace: updated,
                    createNodeSelection: null
                });
            }
        },
        CLOSE: (state) => {
            return Object.assign({}, state, {createNode: null, createNodeSelection: null});
        },
        CANCEL: (state) => {
            return Object.assign({}, state, {createNodeSelection: null});
        }
    },
    [NODE_LINK]: {
        START: (state, action) => {
            return Object.assign({}, state, {linkNode: action.payload});
        },
        FINISH: (state) => {
            return Object.assign({}, state, {linkNode: null});
        }
    }
});
