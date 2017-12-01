import {authedRequest} from '../api-utils';

export const STATISTICS_FETCH = 'STATISTICS_FETCH';

export const STATISTICS_ACTION_PREFIX = 'STATISTICS';

// Statistics ActionCreators
export function fetchStatistics(nodeId) {
    return (dispatch, getState) => {
        const state = getState();
        let lastUpdate = state.lab.lastToolRefresh;
        let cachedStats = state.lab.statistics.get(nodeId);
        if (!cachedStats ||
            cachedStats.error ||
            cachedStats.data && lastUpdate > cachedStats.fetched
           ) {
            dispatch({
                type: STATISTICS_FETCH,
                meta: {nodeId},
                payload: authedRequest({
                    method: 'get',
                    url: `${state.api.tileUrl}/tools/${state.lab.tool.id}` +
                        `/statistics/?node=${nodeId}&voidCache=true&token=${state.api.apiToken}`
                }, state)
            });
        }
    };
}

export default {
    fetchStatistics
};
