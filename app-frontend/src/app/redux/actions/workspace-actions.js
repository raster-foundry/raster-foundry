import _ from 'lodash';
import { authedRequest } from '_api/authentication';

export const WORKSPACE_SET = 'WORKSPACE_SET';
export const WORKSPACE_SET_DIAGRAM= 'WORKSPACE_SET_DIAGRAM';
export const WORKSPACE_UPDATE_NAME = 'WORKSPACE_UPDATE_NAME';
export const WORKSPACE_FETCH = 'WORKSPACE_FETCH';
export const WORKSPACE_SET_OPTIONS = 'WORKSPACE_SET_OPTIONS';

export const WORKSPACE_ACTION_PREFIX = 'WORKSPACE';

export function setWorkspace(workspace) {
    return {
        type: WORKSPACE_SET,
        payload: workspace
    };
}

export function setDiagram(
    nodes, linksBySource, linksByTarget, graph, onShapeMove
) {
    return {
        type: WORKSPACE_SET_DIAGRAM,
        payload: {
            nodes, linksBySource, linksByTarget, graph, onShapeMove
        }
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
    return (dispatch, getState) => {
        let state = getState();
        let currentOptions = {
            readonly: state.lab.readonly,
            controls: state.lab.controls,
            cellDimensions: _.clone(state.lab.cellDimensions),
            nodeSeparationFactor: state.lab.nodeSeparationFactor
        };

        dispatch({
            type: WORKSPACE_SET_OPTIONS,
            payload: Object.assign({}, currentOptions, options)
        });
    };
}

export default {
    setWorkspace, setDiagram, updateWorkspaceName, fetchWorkspace, setDisplayOptions
};
