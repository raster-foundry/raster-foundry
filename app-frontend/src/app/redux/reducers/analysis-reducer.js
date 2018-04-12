import {Map} from 'immutable';
import typeToReducer from 'type-to-reducer';
import {
    ANALYSIS_LOAD, ANALYSIS_UPDATE_NAME, ANALYSIS_FETCH, ANALYSIS_SET_OPTIONS
} from '../actions/lab-actions';

export const analysisReducer = typeToReducer({
    [ANALYSIS_LOAD]: (state, action) => {
        return Object.assign({}, state, {
            analysis: action.payload, lastAnalysisSave: new Date(),
            previewNodes: [], selectedNode: null, nodes: new Map(),
            histograms: new Map()
        });
    },
    [ANALYSIS_UPDATE_NAME]: {
        PENDING: (state, action) => {
            return Object.assign(
                {}, state,
                {
                    analysis: action.meta.analysis,
                    updating: action.forceUpdate ? action.payload.id : false
                }
            );
        },
        REJECTED: (state, action) => {
            return Object.assign({}, state, {
                analysis: action.meta.oldAnalysis, analysisError: action.payload
            });
        },
        FULFILLED: (state, action) => {
            return Object.assign(
                {}, state, {
                    analysis: action.meta.analysis,
                    lastAnalysisSave: new Date()
                }
            );
        }
    },
    [ANALYSIS_FETCH]: {
        PENDING: (state, action) => {
            return Object.assign({}, state, {
                fetching: action.analysisId, analysisErrors: state.analysisErrors.delete('http')
            });
        },
        REJECTED: (state, action) => {
            return Object.assign({}, state, {
                fetching: false, analysisErrors: state.analysisErrors.set('http', action.payload)
            });
        },
        FULFILLED: (state, action) => {
            return Object.assign({}, state, {
                fetching: false,
                analysis: action.payload.data,
                readonly: false,
                lastAnalysisSave: new Date(),
                histograms: new Map(),
                analysisErrors: state.analysisErrors.delete('http')
            });
        }
    },
    [ANALYSIS_SET_OPTIONS]: (state, action) => {
        let payload = action.payload;
        let options = {
            readonly: payload.hasOwnProperty('readonly') ? payload.readonly : false,
            controls: payload.hasOwnProperty('controls') ? payload.controls : true
        };
        return Object.assign({}, state, options);
    }
});
