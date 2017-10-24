import {authedRequest} from '../api-utils';

export const NODE_PREVIEWS = 'NODE_PREVIEWS';
export const NODE_SET_ERROR = 'NODE_SET_ERROR';
export const NODE_INIT = 'NODE_INIT';
// does not affect rendering
export const NODE_UPDATE_SOFT = 'NODE_UPDATE_SOFT';
// force re-render of previews
export const NODE_UPDATE_HARD = 'NODE_UPDATE_HARD';

export const NODE_ACTION_PREFIX = 'NODE';

import { toolFromNodes } from '../node-utils';

// Node ActionCreators

export function previewNode(nodeId) {
    return {
        type: `${NODE_PREVIEWS}_SINGLE`,
        payload: nodeId
    };
}

export function startNodeCompare({nodeId}) {
    return {
        type: `${NODE_PREVIEWS}_START_COMPARE`,
        nodeId
    };
}

export function finishNodeCompare({nodeId}) {
    if (nodeId) {
        return {
            type: `${NODE_PREVIEWS}_FINISH_COMPARE`,
            nodeId
        };
    }
    return {
        type: `${NODE_PREVIEWS}_CANCEL_COMPARE`
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
        let updatedTool = toolFromNodes(labState, updatedNode);
        promise = authedRequest({
            method: 'put',
            url: `${state.api.apiUrl}/api/tool-runs/${labState.tool.id}`,
            data: updatedTool
        }, getState()).then(() => {
            return updatedTool;
        });
        dispatch({
            type: hard ? NODE_UPDATE_HARD : NODE_UPDATE_SOFT,
            meta: {
                node: updatedNode,
                tool: updatedTool
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
    previewNode, startNodeCompare, finishNodeCompare,
    setNodeError, updateNode, initNodes
};
