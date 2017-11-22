import { authedRequest } from '_api/authentication';

export const ANALYSIS_LOAD = 'ANALYSIS_LOAD';
export const ANALYSIS_UPDATE_NAME = 'ANALYSIS_UPDATE_NAME';
export const ANALYSIS_FETCH = 'ANALYSIS_FETCH';

export const ANALYSIS_ACTION_PREFIX = 'ANALYSIS';

// Analysis ActionCreators

export function loadAnalysis(payload, readonly) {
    let action = {type: ANALYSIS_LOAD, payload};
    if (readonly) {
        Object.assign(action, {readonly: true});
    }
    return action;
}

export function updateAnalysisName(name) {
    // TODO update this to use redux-promise instead of the removed middleware
    return (dispatch, getState) => {
        let state = getState();
        let updatedAnalysis = Object.assign({}, state.lab.analysis, {name});
        dispatch({
            type: ANALYSIS_UPDATE_NAME,
            payload: authedRequest({
                method: 'put',
                url: `${state.api.apiUrl}` +
                    `/api/tool-runs/${state.lab.analysis.id}`,
                data: updatedAnalysis
            }, getState()),
            meta: {
                analysis: updatedAnalysis,
                oldAnalysis: Object.assign({}, state.lab.analysis)
            }
        });
    };
}

export function fetchAnalysis(analysisId) {
    return (dispatch, getState) => {
        let state = getState();
        dispatch({
            type: ANALYSIS_FETCH,
            payload: authedRequest(
                {
                    method: 'get',
                    url: `${state.api.apiUrl}/api/tool-runs/${analysisId}`
                },
                state
            )
        });
    };
}

export default {
    loadAnalysis, updateAnalysisName, fetchAnalysis
};
