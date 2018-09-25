/* global _ */
import typeToReducer from 'type-to-reducer';

import {
    PROJECT_SET_MAP, PROJECT_SET_ID, PROJECT_EDIT_LAYER
} from '_redux/actions/project-actions';
const GREEN = '#81C784';

export const projectReducer = typeToReducer({
    [PROJECT_SET_MAP]: (state, action) => {
        return Object.assign({}, state, {
            projectMap: action.payload
        });
    },
    [PROJECT_SET_ID]: (state, action) => {
        return Object.assign({}, state, {
            projectId: action.payload
        });
    },
    [PROJECT_EDIT_LAYER]: {
        START: (state, action) => {
            const geometry = action.payload.geometry;
            const options = Object.assign({
                draggable: true,
                fillColor: GREEN,
                color: GREEN,
                opacity: 0.9,
                pane: 'editable'
            }, action.payload.options);
            const mapWrapper = state.projectMap;
            let editHandler;
            if (geometry.type === 'Polygon') {
                let coordinates = geometry.coordinates[0].map(c => [c[1], c[0]]);
                let polygonLayer = L.polygon(
                    coordinates,
                    options
                );
                mapWrapper.setLayer('draw', polygonLayer, false);
                if (_.get(action, 'payload.meta.panTo')) {
                    mapWrapper.map.panTo(polygonLayer.getCenter());
                }
                editHandler = new L.EditToolbar.Edit(mapWrapper.map, {
                    featureGroup: L.featureGroup([polygonLayer])
                });
                editHandler.enable();
            } else if (geometry.type === 'Point') {
                let markerLayer = L.marker([geometry.coordinates[1], geometry.coordinates[0]], {
                    'icon': L.divIcon({
                        'className': 'annotate-clone-marker'
                    }),
                    'draggable': true
                });
                mapWrapper.setLayer('draw', markerLayer, false);
                if (_.get(action, 'payload.meta.panTo')) {
                    mapWrapper.map.panTo([geometry.coordinates[1], geometry.coordinates[0]]);
                }
            }
            return Object.assign({}, state, {
                editHandler
            });
        },
        FINISH: (state) => {
            let editHandler = state.editHandler;
            // eslint-disable-next-line
            if (editHandler && editHandler._enabled) {
                editHandler.disable();
            }
            state.projectMap.deleteLayers('draw');
            return Object.assign({}, state, {
                editHandler: null
            });
        }
    }
});
