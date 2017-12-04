import {authedRequest} from '../api-utils';

export const NODE_PREVIEWS = 'NODE_PREVIEWS';
export const NODE_SET_ERROR = 'NODE_SET_ERROR';
export const NODE_INIT = 'NODE_INIT';
// does not affect rendering
export const NODE_UPDATE_SOFT = 'NODE_UPDATE_SOFT';
// force re-render of previews
export const NODE_UPDATE_HARD = 'NODE_UPDATE_HARD';

export const NODE_ACTION_PREFIX = 'NODE';

import { astFromNodes } from '../node-utils';

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
    return {
        type: `${NODE_PREVIEWS}_SELECT_NODE`,
        nodeId
    };
}

export function compareNodes(nodes) {
    return {
        type: `${NODE_PREVIEWS}_COMPARE_NODES`,
        nodes
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
        let updatedAnalysis = astFromNodes(labState, updatedNode);
        promise = authedRequest({
            method: 'put',
            url: `${state.api.apiUrl}/api/tool-runs/${labState.tool.id}`,
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
    startNodePreview, startNodeCompare, selectNode, cancelNodeSelect,
    setNodeError, updateNode, initNodes
};
