import {
    getProjectAnnotationsRequest, createProjectAnnotationsRequest, updateProjectAnnotationRequest,
    getProjectLabelsRequest, clearProjectAnnotationsRequest, deleteProjectAnnotationRequest,
    uploadShapefileOnly, uploadShapefileWithProps
} from '_api/annotations';

// IF YOU ADD CONSTANTS:
// They MUST be prefixed with whatever the value of ANNOTATIONS_ACTION_PREFIX is
// For example, if you prefix with ANNOTATION instead of ANNOTATIONS, your state changes
// will never happen, and you'll be sad, and you won't feel very smart.

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
export const ANNOTATIONS_UPLOAD_SHAPEFILE = 'ANNOTATIONS_UPLOAD_SHAPEFILE';
export const ANNOTATIONS_IMPORT_SHAPEFILE = 'ANNOTATIONS_IMPORT_SHAPEFILE';
export const ANNOTATIONS_UPLOAD_SHAPEFILE_DELETE = 'ANNOTATIONS_UPLOAD_SHAPEFILE_DELETE';

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

export function createAnnotations(annotations, edit, panTo) {
    return (dispatch, getState) => {
        dispatch({
            type: ANNOTATIONS_CREATE,
            payload: createProjectAnnotationsRequest(getState(), annotations),
            meta: {
                edit,
                panTo
            }
        });
    };
}

export function uploadShapefile(shapefile) {
    return (dispatch, getState) => {
        dispatch({
            type: ANNOTATIONS_UPLOAD_SHAPEFILE,
            payload: uploadShapefileOnly(getState(), shapefile)
        });
    };
}

export function deleteShapeFileUpload() {
    return (dispatch, getState) => {
        dispatch({
            type: ANNOTATIONS_UPLOAD_SHAPEFILE_DELETE
        });
    };
}

export function importShapefileWithProps(shapefile, matchedKeys) {
    return (dispatch, getState) => {
        dispatch({
            type: ANNOTATIONS_IMPORT_SHAPEFILE,
            payload: uploadShapefileWithProps(getState(), shapefile, matchedKeys)
        });
    };
}

export function updateAnnotation(annotation) {
    return (dispatch, getState) => {
        let state = getState();
        dispatch({
            type: ANNOTATIONS_UPDATE,
            payload: updateProjectAnnotationRequest(state, annotation),
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
                payload: deleteProjectAnnotationRequest(state, annotationId),
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
    transformDrawlayer, uploadShapefile, importShapefileWithProps,
    deleteShapeFileUpload
};
