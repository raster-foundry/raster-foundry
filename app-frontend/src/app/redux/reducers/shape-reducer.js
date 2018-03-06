import typeToReducer from 'type-to-reducer';

import {
    SHAPES_DRAW,
    SHAPES_EDIT} from '_redux/actions/shape-actions';

import {
    PROJECT_EDIT_LAYER
} from '_redux/actions/project-actions';

export const shapeReducer = typeToReducer({
    [SHAPES_DRAW]: {
        START: (state, action) => {
            return Object.assign({}, state, {
                mapId: action.payload.mapId,
                resolve: action.payload.resolve,
                reject: action.payload.reject
            });
        },
        FINISH: (state) => {
            return Object.assign({}, state, {
                mapId: null,
                resolve: null,
                reject: null
            });
        }
    },
    [SHAPES_EDIT]: {
        START: (state, action) => {
            action.asyncDispatch({
                type: `${PROJECT_EDIT_LAYER}_START`,
                payload: {
                    geometry: action.payload.geometry,
                    options: {}
                }
            });

            return Object.assign({}, state, {
                resolveEdit: action.payload.resolve,
                rejectEdit: action.payload.reject
            });
        },
        FINISH: (state, action) => {
            action.asyncDispatch({
                type: `${PROJECT_EDIT_LAYER}_FINISH`
            });

            return Object.assign({}, state, {
                resolveEdit: null,
                rejectEdit: null
            });
        }
    }
});
