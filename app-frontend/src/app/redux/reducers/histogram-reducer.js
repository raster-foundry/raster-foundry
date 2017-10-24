import typeToReducer from 'type-to-reducer';

import {HISTOGRAM_FETCH} from '../actions/histogram-actions';

export const histogramReducer = typeToReducer({
    [HISTOGRAM_FETCH]: {
        PENDING: (state, action) => {
            return Object.assign({}, state, {
                histograms: state.histograms.set(
                    action.meta.nodeId,
                    {fetching: true}
                )
            });
        },
        REJECTED: (state, action) => {
            return Object.assign({}, state, {
                histograms: state.histograms.set(
                    action.meta.nodeId,
                    {error: action.payload}
                )
            });
        },
        FULFILLED: (state, action) => {
            let response = action.payload;
            let nodeId = action.meta.nodeId;
            return Object.assign({}, state, {
                histograms: state.histograms.set(nodeId, {data: response.data, fetched: new Date()})
            });
        }
    }
});
