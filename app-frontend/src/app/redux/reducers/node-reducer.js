import typeToReducer from 'type-to-reducer';
import _ from 'lodash';

import {
    NODE_PREVIEWS, NODE_SET_ERROR, NODE_UPDATE, NODE_SET, NODE_LINKS_SET,
    NODE_CREATE, NODE_LINK
} from '../actions/node-actions';

export const nodeReducer = typeToReducer({
    [NODE_SET]: (state, action) => {
        return Object.assign({}, state, {nodes: action.payload});
    },
    [NODE_LINKS_SET]: (state, action) => {
        return Object.assign({}, state, {
            linksBySource: action.payload.linksBySource,
            linksByTarget: action.payload.linksByTarget
        });
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
    [NODE_UPDATE]: {
        PENDING: (state) => {
            return state;
        },
        REJECTED: (state) => {
            return state;
        },
        FULFILLED: (state, action) => {
            let workspace = _.clone(state.workspace);
            let analyses = action.meta.analyses;
            analyses.forEach((analysis) => {
                let analysisIndex = _.findIndex(workspace.analyses, (a) => a.id === analysis.id);
                workspace.analyses[
                    analysisIndex >= 0 ? analysisIndex : workspace.analyses.length
                ] = analysis;

                if (action.meta.deleteAnalysis) {
                    let deleteAnalysisId = action.meta.deleteAnalysis;
                    _.remove(workspace.analyses, (a) => a.id === deleteAnalysisId);
                }
            });

            const stateUpdate = _.omitBy({
                previewNodes: state.previewNodes.concat([]),
                workspace,
                nodes: action.meta.nodes,
                linksByTarget: action.meta.linksByTarget,
                linksBySource: action.meta.linksBySource
            }, _.isUndefined);

            return Object.assign({}, state, stateUpdate);
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
                    nodes: state.nodes.merge(action.meta.nodes),
                    linksBySource: state.linksBySource.merge(action.meta.linksBySource),
                    linksByTarget: state.linksByTarget.merge(action.meta.linksByTarget),
                    workspace: updated,
                    analysisRoots: state.analysisRoots.set(
                        analysis.id, analysis.executionParameters.id
                    ),
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
        },
        DELETE: {
            PENDING: (state) => {
                return state;
            },
            REJECTED: (state) => {
                return state;
            },
            FULFILLED: (state, action) => {
                let analysisRoots = state.lab.analysisRoots;
                let analyses = action.payload;
                let workspace = state.workspace;
                analyses.forEach((analysis) => {
                    if (!analysisRoots.has(analysis.id)) {
                        analysisRoots = analysisRoots.set(
                            analysis.id, analysis.executionParameters.id
                        );
                        workspace.analyses = workspace.analyses.concat([analysis]);
                    }
                });
                return Object.assign({}, state, {
                    analysisRoots,
                    linksBySource: action.linksBySource,
                    linksByTarget: action.linksByTarget,
                    nodes: action.nodes,
                    workspace: workspace
                });
            }
        }
    }
});
