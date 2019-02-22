export const PROJECT_SET_MAP = 'PROJECT_SET_MAP';
export const PROJECT_SET_ID = 'PROJECT_SET_ID';
export const PROJECT_EDIT_LAYER = 'PROJECT_EDIT_LAYER';
export const PROJECT_ACTION_PREFIX = 'PROJECT';
export const PROJECT_LAYER_SET_ID = 'PROJECT_LAYER_SET_ID';

export function setProjectMap(map) {
    return {
        type: PROJECT_SET_MAP,
        payload: map
    };
}

export function setProjectId(id) {
    return {
        type: PROJECT_SET_ID,
        payload: id
    };
}

export function setLayerId(id) {
    return {
        type: PROJECT_LAYER_SET_ID,
        payload: id
    };
}

export default {
    setProjectMap, setProjectId, setLayerId
};
