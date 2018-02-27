import { authedRequest } from '_api/authentication';

export const WORKSPACE_LOAD = 'WORKSPACE_LOAD';
export const WORKSPACE_UPDATE_NAME = 'WORKSPACE_UPDATE_NAME';
export const WORKSPACE_FETCH = 'WORKSPACE_FETCH';
export const WORKSPACE_SET_OPTIONS = 'WORKSPACE_SET_OPTIONS';

export const WORKSPACE_ACTION_PREFIX = 'WORKSPACE';

export function loadWorkspace(workspace, options) {
    return (dispatch) => {
        let loadAction = {type: WORKSPACE_LOAD, payload: workspace};
        let optionAction = {type: WORKSPACE_SET_OPTIONS, payload: options};
        if (options) {
            dispatch(optionAction);
        }
        dispatch(loadAction);
    };
}

export function updateWorkspaceName(name) {
    // TODO update this to use redux-promise instead of the removed middleware
    return (dispatch, getState) => {
        let state = getState();
        let updatedWorkspace = Object.assign({}, state.lab.workspace, {name});
        dispatch({
            type: WORKSPACE_UPDATE_NAME,
            payload: authedRequest({
                method: 'put',
                url: `${state.api.apiUrl}` +
                    `/api/workspaces/${state.lab.workspace.id}`,
                data: updatedWorkspace
            }, getState()),
            meta: {
                workspace: updatedWorkspace,
                oldWorkspace: Object.assign({}, state.lab.workspace)
            }
        });
    };
}

export function fetchWorkspace(workspaceId) {
    return (dispatch, getState) => {
        let state = getState();
        dispatch({
            type: WORKSPACE_FETCH,
            payload: authedRequest(
                {
                    method: 'get',
                    url: `${state.api.apiUrl}/api/workspaces/${workspaceId}`
                },
                state
            )
        });
    };
}

export function setDisplayOptions(options) {
    return {type: WORKSPACE_SET_OPTIONS, options};
}

export default {
    loadWorkspace, updateWorkspaceName, fetchWorkspace, setDisplayOptions
};
