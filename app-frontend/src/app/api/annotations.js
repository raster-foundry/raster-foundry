import {authedRequest} from './authentication';


export function getProjectAnnotationsRequest(state, params) {
    return authedRequest({
        method: 'get',
        url: `${state.api.apiUrl}` +
            `/api/projects/${state.projects.projectId}/annotations`,
        params
    }, state);
}

export function getProjectLabelsRequest(state) {
    return authedRequest({
        method: 'get',
        url: `${state.api.apiUrl}` +
            `/api/projects/${state.projects.projectId}/labels`
    }, state);
}

export function createProjectAnnotationsRequest(state, annotationGeojson) {
    return authedRequest({
        method: 'post',
        url: `${state.api.apiUrl}` +
            `/api/projects/${state.projects.projectId}/annotations`,
        data: annotationGeojson
    }, state);
}

export function updateProjectAnnotationRequest(state, annotation) {
    return authedRequest({
        method: 'put',
        url: `${state.api.apiUrl}` +
            `/api/projects/${state.projects.projectId}/annotations/${annotation.id}`,
        data: annotation
    }, state);
}

export function deleteProjectAnnotationRequest(state, annotationId) {
    return authedRequest({
        method: 'delete',
        url: `${state.api.apiUrl}` +
            `/api/projects/${state.projects.projectId}/annotations/${annotationId}`
    }, state);
}

export function clearProjectAnnotationsRequest(state) {
    return authedRequest({
        method: 'delete',
        url: `${state.api.apiUrl}` +
            `/api/projects/${state.projects.projectId}/annotations/`
    }, state);
}
