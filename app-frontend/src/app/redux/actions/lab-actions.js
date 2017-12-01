import { authedRequest } from '../api-utils';

export const TOOL_LOAD = 'TOOL_LOAD';
export const TOOL_UPDATE_NAME = 'TOOL_UPDATE_NAME';
export const TOOL_FETCH = 'TOOL_FETCH';

export const TOOL_ACTION_PREFIX = 'TOOL';

// Tool ActionCreators

export function loadTool(payload, readonly) {
    let action = {type: TOOL_LOAD, payload};
    if (readonly) {
        Object.assign(action, {readonly: true});
    }
    return action;
}

export function updateToolName(name) {
    // TODO update this to use redux-promise instead of the removed middleware
    return (dispatch, getState) => {
        let state = getState();
        let updatedTool = Object.assign({}, state.lab.tool, {name});
        dispatch({
            type: TOOL_UPDATE_NAME,
            payload: authedRequest({
                method: 'put',
                url: `${state.api.apiUrl}` +
                    `/api/tool-runs/${state.lab.tool.id}`,
                data: updatedTool
            }, getState()),
            meta: {
                tool: updatedTool,
                oldTool: Object.assign({}, state.lab.tool)
            }
        });
    };
}

export function fetchTool(toolId) {
    return (dispatch, getState) => {
        let state = getState();
        dispatch({
            type: TOOL_FETCH,
            payload: authedRequest(
                {
                    method: 'get',
                    url: `${state.api.apiUrl}/api/tool-runs/${toolId}`
                },
                state
            )
        });
    };
}

export default {
    loadTool, updateToolName, fetchTool
};
