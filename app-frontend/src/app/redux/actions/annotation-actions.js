import {
    getProjectAnnotationsRequest, createProjectAnnotationsRequest, updateProjectAnnotationRequest,
    getProjectLabelsRequest, clearProjectAnnotationsRequest, deleteProjectAnnotationRequest
} from '_api/annotations';

export const ANNOTATIONS_RESET = 'ANNOTATIONS_RESET';
export const ANNOTATIONS_FETCH = 'ANNOTATIONS_FETCH';
export const ANNOTATIONS_LABELS_FETCH = 'ANNOTATIONS_LABELS_FETCH';
export const ANNOTATIONS_CREATE = 'ANNOTATIONS_CREATE';
export const ANNOTATIONS_UPDATE = 'ANNOTATIONS_UPDATE';
export const ANNOTATIONS_FILTER = 'ANNOTATIONS_FILTER';
export const ANNOTATIONS_CLEAR = 'ANNOTATIONS_CLEAR';
export const ANNOTATIONS_DELETE = 'ANNOTATIONS_DELETE';
export const ANNOTATIONS_EDIT = 'ANNOTATIONS_EDIT';
export const ANNOTATIONS_BULK_CREATE = 'ANNOTATIONS_BULK_CREATE';
export const ANNOTATIONS_SIDEBAR = 'ANNOTATIONS_SIDEBAR';
export const ANNOTATIONS_TRANSFORM_DRAWLAYER = 'ANNOTATIONS_TRANSFORM_DRAWLAYER';

export const ANNOTATIONS_ACTION_PREFIX = 'ANNOTATIONS';


export function enableSidebar() {
    return {
        type: `${ANNOTATIONS_SIDEBAR}_ENABLE`
    };
}

export function disableSidebar() {
    return {
        type: `${ANNOTATIONS_SIDEBAR}_DISABLE`
    };
}

export function resetAnnotations() {
    return {
        type: ANNOTATIONS_RESET
    };
}

export function fetchAnnotations(page) {
    return (dispatch, getState) => {
        const state = getState();
        dispatch({
            type: ANNOTATIONS_FETCH,
            payload: getProjectAnnotationsRequest(
                state, {
                    page: page ? page : 0
                }
            ),
            meta: {
                state
            }
        });
    };
}

export function fetchLabels() {
    return (dispatch, getState) => {
        dispatch({
            type: ANNOTATIONS_LABELS_FETCH,
            payload: getProjectLabelsRequest(getState())
        });
    };
}

export function createAnnotations(annotations, edit) {
    return (dispatch, getState) => {
        dispatch({
            type: ANNOTATIONS_CREATE,
            payload: createProjectAnnotationsRequest(annotations, getState()),
            meta: {
                edit
            }
        });
    };
}

export function updateAnnotation(annotation) {
    return (dispatch, getState) => {
        let state = getState();
        dispatch({
            type: ANNOTATIONS_UPDATE,
            payload: updateProjectAnnotationRequest(annotation, state),
            meta: {
                annotation,
                state
            }
        });
    };
}

export function filterAnnotations(filter) {
    return {
        type: ANNOTATIONS_FILTER,
        payload: filter
    };
}

export function clearAnnotations() {
    return (dispatch, getState) => {
        dispatch({
            type: ANNOTATIONS_CLEAR,
            payload: clearProjectAnnotationsRequest(getState())
        });
    };
}

export function editAnnotation(annotationId) {
    return {
        type: `${ANNOTATIONS_EDIT}_START`,
        payload: annotationId
    };
}

export function finishEditingAnnotation(annotation) {
    if (annotation) {
        return (dispatch, getState) => {
            dispatch({
                type: `${ANNOTATIONS_EDIT}_FINISH`,
                payload: annotation,
                meta: {
                    state: getState()
                }
            });
        };
    }
    return {
        type: `${ANNOTATIONS_EDIT}_CANCEL`
    };
}

export function deleteAnnotation(annotationId) {
    if (annotationId && annotationId.length) {
        return (dispatch, getState) => {
            let state = getState();
            dispatch({
                type: ANNOTATIONS_DELETE,
                payload: deleteProjectAnnotationRequest(annotationId, state),
                meta: {
                    annotationId,
                    state
                }
            });
        };
    }
    // eslint-disable-next-line
    console.error('Cannot delete an annotation without an id');
    return {type: ''};
}

export function bulkCreateAnnotations(annotation) {
    return {
        type: `${ANNOTATIONS_BULK_CREATE}_START`,
        payload: annotation
    };
}

export function finishBulkCreate() {
    return {
        type: `${ANNOTATIONS_BULK_CREATE}_FINISH`
    };
}

export function transformDrawlayer(transform, options) {
    return {
        type: `${ANNOTATIONS_TRANSFORM_DRAWLAYER}`,
        payload: {
            transform, options
        }
    };
}

export default {
    resetAnnotations, enableSidebar, disableSidebar,
    fetchAnnotations, fetchLabels,
    createAnnotations, updateAnnotation, filterAnnotations,
    clearAnnotations, editAnnotation, finishEditingAnnotation,
    deleteAnnotation, bulkCreateAnnotations, finishBulkCreate,
    transformDrawlayer
};
