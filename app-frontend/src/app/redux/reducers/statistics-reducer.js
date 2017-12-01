import typeToReducer from 'type-to-reducer';

import {STATISTICS_FETCH} from '../actions/statistics-actions';

export const statisticsReducer = typeToReducer({
    [STATISTICS_FETCH]: {
        PENDING: (state, action) => {
            return Object.assign({}, state, {
                statistics: state.statistics.set(
                    action.meta.nodeId,
                    {fetching: true}
                )
            });
        },
        REJECTED: (state, action) => {
            return Object.assign({}, state, {
                statistics: state.statistics.set(
                    action.meta.nodeId,
                    {error: action.payload}
                )
            });
        },
        FULFILLED: (state, action) => {
            let response = action.payload;
            let nodeId = action.meta.nodeId;
            return Object.assign({}, state, {
                statistics: state.statistics.set(nodeId, {data: response.data, fetched: new Date()})
            });
        }
    }
});
