import {Map} from 'immutable';
import typeToReducer from 'type-to-reducer';
import {
    ANALYSIS_LOAD, ANALYSIS_UPDATE_NAME, ANALYSIS_FETCH
} from '../actions/lab-actions';

const resetLabState = () => ({
    lastAnalysisSave: new Date(),
    lastAnalysisRefresh: new Date(),
    analysis: null,
    analysisErrors: new Map(),
    updating: false, fetching: false, error: null,
    readonly: false,

    nodes: new Map(), previewNodes: [], selectingNode: null, selectedNode: null,
    preventSelecting: false,

    histograms: new Map(), statistics: new Map()
});

export const analysisReducer = typeToReducer({
    [ANALYSIS_LOAD]: (state, action) => {
        return Object.assign({}, state, resetLabState(), {
            analysis: action.payload,
            readonly: action.readonly
        });
    },
    [ANALYSIS_FETCH]: {
        PENDING: (state, action) => {
            return Object.assign({}, state, resetLabState(), {
                fetching: action.analysisId,
                readonly: action.readonly
            });
        },
        REJECTED: (state, action) => {
            return Object.assign({}, state, {
                fetching: false,
                analysisErrors: state.analysisErrors.set('http', action.payload)
            });
        },
        FULFILLED: (state, action) => {
            return Object.assign({}, state, {
                analysis: action.payload.data,
                analysisErrors: new Map(),
                fetching: false
            });
        }
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
    }
});
