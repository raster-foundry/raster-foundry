export const SHAPES_DRAW = 'SHAPES_DRAW';
export const SHAPES_EDIT = 'SHAPES_EDIT';

export const SHAPES_ACTION_PREFIX = 'SHAPES';

export function startDrawing(resolve, reject, mapId) {
    return {
        type: `${SHAPES_DRAW}_START`,
        payload: {resolve, reject, mapId}
    };
}

export function cancelDrawing() {
    return (dispatch, getState) => {
        const state = getState();
        if (state.shape.reject) {
            state.shape.reject();
        }
        dispatch({
            type: `${SHAPES_DRAW}_FINISH`
        });
    };
}

export function finishDrawing(geojson) {
    return (dispatch, getState) => {
        const state = getState();
        if (state.shape.resolve) {
            state.shape.resolve(geojson);
        }
        dispatch({
            type: `${SHAPES_DRAW}_FINISH`,
            payload: geojson
        });
    };
}

export function startEditingShape(geometry, resolve, reject) {
    return {
        type: `${SHAPES_EDIT}_START`,
        payload: {geometry, resolve, reject}
    };
}

export function finishEditingShape(geometry) {
    return (dispatch, getState) => {
        const state = getState();
        if (state.shape.resolveEdit) {
            state.shape.resolveEdit(geometry);
        }
        dispatch({
            type: `${SHAPES_EDIT}_FINISH`,
            payload: geometry
        });
    };
}

export function cancelEditingShape() {
    return (dispatch, getState) => {
        const state = getState();
        if (state.shape.rejectEdit) {
            state.shape.rejectEdit();
        }
        dispatch({
            type: `${SHAPES_EDIT}_FINISH`
        });
    };
}

export default {
    startDrawing, cancelDrawing, finishDrawing,
    startEditingShape, finishEditingShape, cancelEditingShape
};
