import {authedRequest} from '../api-utils';
import {colorStopsToRange} from '../histogram-utils';
import {getNodeDefinition, toolFromNodes} from '../node-utils';
import {NODE_UPDATE_HARD} from './node-actions';

const { colorSchemes: colorSchemes } = require('../../services/projects/colorScheme.defaults.json');

export const HISTOGRAM_FETCH = 'HISTOGRAM_FETCH';
export const HISTOGRAM_UPDATE = 'HISTOGRAM_UPDATE';

export const HISTOGRAM_ACTION_PREFIX = 'HISTOGRAM';

function createRenderDefinition(histogram) {
    let min = histogram.minimum;
    let max = histogram.maximum;

    let defaultColorScheme = colorSchemes.find(
        s => s.label === 'Viridis'
    );
    let breakpoints = colorStopsToRange(defaultColorScheme.colors, min, max);
    let renderDefinition = {clip: 'none', scale: 'SEQUENTIAL', breakpoints};
    let histogramOptions = {range: {min, max}, baseScheme: {
        colorScheme: Object.entries(defaultColorScheme.colors)
            .map(([key, val]) => ({break: key, color: val}))
            .sort((a, b) => a.break - b.break)
            .map((c) => c.color),
        dataType: 'SEQUENTIAL',
        colorBins: 0
    }};

    return {
        renderDefinition,
        histogramOptions
    };
}

// Histogram ActionCreators
export function fetchHistogram(nodeId) {
    return (dispatch, getState) => {
        let state = getState();
        let lastUpdate = state.lab.lastToolRefresh;
        let cachedHistogram = state.lab.histograms.get(nodeId);

        if (!cachedHistogram ||
            cachedHistogram.error ||
            cachedHistogram.data && lastUpdate > cachedHistogram.fetched
           ) {
            dispatch({
                type: HISTOGRAM_FETCH,
                meta: {nodeId},
                payload: authedRequest(
                    {
                        method: 'get',
                        url: `${state.api.tileUrl}/tools/${state.lab.tool.id}`
                            + `/histogram?node=${nodeId}&voidCache=true&token=${state.api.apiToken}`
                    },
                    state
                )
            }).then(
                ({value: response}) => {
                    let histogram = response.data;
                    let callbackState = getState();
                    let nodeDefinition = getNodeDefinition(getState(), {nodeId});
                    if (!nodeDefinition.metadata.renderDefinition) {
                        let {
                            renderDefinition,
                            histogramOptions
                        } = createRenderDefinition(histogram);
                        let newNodeDefinition = Object.assign(
                            {}, nodeDefinition,
                            {
                                metadata: Object.assign({}, nodeDefinition.metadata, {
                                    renderDefinition,
                                    histogramOptions
                                })
                            }
                        );
                        let updatedTool = toolFromNodes(callbackState.lab, newNodeDefinition);
                        let promise = authedRequest({
                            method: 'put',
                            url: `${callbackState.api.apiUrl}` +
                                `/api/tool-runs/${callbackState.lab.tool.id}`,
                            data: updatedTool
                        }, callbackState);
                        dispatch({
                            type: NODE_UPDATE_HARD,
                            payload: promise,
                            meta: {
                                node: newNodeDefinition,
                                tool: updatedTool
                            }
                        });
                    }
                }
            ).catch(() => {});
            // ignore network errors, they are handled by reducer
        }
    };
}

/**
  * Args:
  * Object {
  *     nodeId: node id in the tool tree
  *     renderDefinition: render definition to update with
  *     histogramOptions (optional)
  * }
  * @return {thunk} dispatching function
  */
export function updateRenderDefinition({nodeId, renderDefinition, histogramOptions}) {
    return (dispatch, getState) => {
        const state = getState();
        const nodeDefinition = state.lab.nodes.get(nodeId);
        const newNodeDefinition = Object.assign({}, nodeDefinition, {
            metadata: Object.assign({}, nodeDefinition.metadata, {
                renderDefinition,
                histogramOptions: histogramOptions ?
                    histogramOptions : nodeDefinition.metadata.histogramOptions
            })
        });

        const updatedTool = toolFromNodes(state.lab, newNodeDefinition);
        dispatch({
            type: NODE_UPDATE_HARD,
            payload: authedRequest({
                method: 'put',
                url: `${state.api.apiUrl}` +
                    `/api/tool-runs/${state.lab.tool.id}`,
                data: updatedTool
            }, state),
            meta: {
                node: newNodeDefinition,
                tool: updatedTool
            }
        });
    };
}

export default {
    fetchHistogram, updateRenderDefinition
};
