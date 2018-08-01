import {authedRequest} from '_api/authentication';
import {Promise} from 'es6-promise';
import _ from 'lodash';

export const NODE_PREVIEWS = 'NODE_PREVIEWS';
export const NODE_SET_ERROR = 'NODE_SET_ERROR';
export const NODE_INIT = 'NODE_INIT';
// does not affect rendering
export const NODE_UPDATE_SOFT = 'NODE_UPDATE_SOFT';
// force re-render of previews
export const NODE_UPDATE_HARD = 'NODE_UPDATE_HARD';

export const NODE_ACTION_PREFIX = 'NODE';

import { astFromNodes, createNodeMetadata } from '../node-utils';

// Node ActionCreators

export function startNodePreview() {
    return {
        type: `${NODE_PREVIEWS}_START_PREVIEW`
    };
}

export function startNodeCompare() {
    return {
        type: `${NODE_PREVIEWS}_START_COMPARE`
    };
}

export function selectNode(nodeId) {
    return (dispatch, getState) => {
        const state = getState();
        const labState = state.lab;
        const nodes = labState.nodes;
        const selectedNode = nodes.get(nodeId);
        const renderDef = selectedNode.metadata.renderDefinition;
        if (!renderDef) {
            dispatch({
                type: `${NODE_PREVIEWS}_PAUSE_SELECT`
            });
            createNodeMetadata(state, {node: selectedNode})
                .then((metadata) => {
                    return _.merge({}, selectedNode, {metadata});
                })
                .then((updatedNode) => {
                    const updatedAnalysis = astFromNodes(labState, [updatedNode]);
                    const nodeUpdateRequest = authedRequest({
                        method: 'put',
                        url: `${state.api.apiUrl}/api/tool-runs/${labState.analysis.id}`,
                        data: updatedAnalysis
                    }, state);
                    dispatch({
                        type: NODE_UPDATE_HARD,
                        meta: {
                            node: updatedNode,
                            analysis: updatedAnalysis
                        },
                        payload: nodeUpdateRequest
                    });
                    return nodeUpdateRequest.then(() => updatedNode);
                }).then((updatedNode) => {
                    dispatch({
                        type: `${NODE_PREVIEWS}_SELECT_NODE`,
                        nodeId: updatedNode.id
                    });
                }).catch((error) => {
                    // eslint-disable-next-line
                    console.log('Error while updating nodes before preview', error);
                    dispatch({
                        type: `${NODE_PREVIEWS}_ERROR`,
                        error: error
                    });
                });
        } else {
            dispatch({
                type: `${NODE_PREVIEWS}_SELECT_NODE`,
                nodeId
            });
        }
    };
}

export function compareNodes(nodeIds) {
    return (dispatch, getState) => {
        const state = getState();
        const labState = state.lab;
        const nodes = labState.nodes;
        const selectedNodes = nodeIds.map((id) => nodes.get(id));
        const missingRenderDefs = selectedNodes.filter((node) => !node.metadata.renderDefinition);
        if (missingRenderDefs.length) {
            dispatch({
                type: `${NODE_PREVIEWS}_PAUSE_SELECT`
            });
            const renderDefPromises = missingRenderDefs.map((node) => {
                return createNodeMetadata(state, {node})
                    .then((metadata) => {
                        return _.merge({}, node, {metadata});
                    });
            });
            Promise.all(
                renderDefPromises
            ).then((nodesToUpdate) => {
                const updatedNodes = nodesToUpdate;
                const updatedAnalysis = astFromNodes(labState, updatedNodes);
                const nodeUpdateRequest = authedRequest({
                    method: 'put',
                    url: `${state.api.apiUrl}/api/tool-runs/${labState.analysis.id}`,
                    data: updatedAnalysis
                }, state);
                dispatch({
                    type: NODE_UPDATE_HARD,
                    meta: {
                        nodes: updatedNodes,
                        analysis: updatedAnalysis
                    },
                    payload: nodeUpdateRequest
                });
                return nodeUpdateRequest.then(() => nodesToUpdate);
            }).then((updatedNodes) => {
                dispatch({
                    type: `${NODE_PREVIEWS}_COMPARE_NODES`,
                    nodes: updatedNodes
                });
            }).catch((error) => {
                // eslint-disable-next-line
                console.log('Error while updating nodes before preview', error);
                dispatch({
                    type: `${NODE_PREVIEWS}_ERROR`,
                    error: error
                });
            });
        } else {
            dispatch({
                type: `${NODE_PREVIEWS}_COMPARE_NODES`,
                nodes: nodeIds
            });
        }
    };
}

export function cancelNodeSelect() {
    return {
        type: `${NODE_PREVIEWS}_CANCEL_SELECT`
    };
}

export function setNodeError({nodeId, error}) {
    return {
        type: NODE_SET_ERROR,
        nodeId,
        payload: error
    };
}

export function updateNode({payload, hard = false}) {
    // TODO handle multiple updates close to each other better
    //      currently histograms do not reflect reality if you
    //      update a second time before the histogram finished fetching.
    //      maybe store initial request times and compare that way, instead of
    //      finish times
    return (dispatch, getState) => {
        let state = getState();
        let labState = getState().lab;
        let promise;
        let updatedNode = payload;
        let updatedAnalysis = astFromNodes(labState, [updatedNode]);
        promise = authedRequest({
            method: 'put',
            url: `${state.api.apiUrl}/api/tool-runs/${labState.analysis.id}`,
            data: updatedAnalysis
        }, getState()).then(() => {
            return updatedAnalysis;
        });
        dispatch({
            type: hard ? NODE_UPDATE_HARD : NODE_UPDATE_SOFT,
            meta: {
                node: updatedNode,
                analysis: updatedAnalysis
            },
            payload: promise
        });
    };
}

export function initNodes(payload) {
    return {
        type: NODE_INIT,
        payload
    };
}

export default {
    startNodePreview, startNodeCompare, selectNode, cancelNodeSelect, compareNodes,
    setNodeError, updateNode, initNodes
};
