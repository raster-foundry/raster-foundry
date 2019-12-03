/* global FormData */

import { authedRequest } from './authentication';
import _ from 'lodash';

export function getProjectAnnotationsRequest(state, params) {
    if (_.get(state, 'projects.layerId')) {
        return authedRequest(
            {
                method: 'get',
                url:
                    `${state.api.apiUrl}` +
                    `/api/projects/${state.projects.projectId}` +
                    `/layers/${state.projects.layerId}/annotations`,
                params
            },
            state
        );
    }
    return authedRequest(
        {
            method: 'get',
            url: `${state.api.apiUrl}` + `/api/projects/${state.projects.projectId}/annotations`,
            params
        },
        state
    );
}

export function getProjectLabelsRequest(state) {
    if (_.get(state, 'projects.layerId')) {
        return authedRequest(
            {
                method: 'get',
                url:
                    `${state.api.apiUrl}` +
                    `/api/projects/${state.projects.projectId}` +
                    `/layers/${state.projects.layerId}/labels`
            },
            state
        );
    }
    return authedRequest(
        {
            method: 'get',
            url: `${state.api.apiUrl}` + `/api/projects/${state.projects.projectId}/labels`
        },
        state
    );
}

export function createProjectAnnotationsRequest(state, annotationGeojson) {
    if (_.get(state, 'projects.layerId')) {
        return authedRequest(
            {
                method: 'post',
                url:
                    `${state.api.apiUrl}` +
                    `/api/projects/${state.projects.projectId}` +
                    `/layers/${state.projects.layerId}/annotations`,
                data: annotationGeojson
            },
            state
        );
    }
    return authedRequest(
        {
            method: 'post',
            url: `${state.api.apiUrl}` + `/api/projects/${state.projects.projectId}/annotations`,
            data: annotationGeojson
        },
        state
    );
}

export function updateProjectAnnotationRequest(state, annotation) {
    if (_.get(state, 'projects.layerId')) {
        return authedRequest(
            {
                method: 'put',
                url:
                    `${state.api.apiUrl}` +
                    `/api/projects/${state.projects.projectId}/` +
                    `/layers/${state.projects.layerId}/annotations/${annotation.id}`,
                data: annotation
            },
            state
        );
    }
    return authedRequest(
        {
            method: 'put',
            url:
                `${state.api.apiUrl}` +
                `/api/projects/${state.projects.projectId}/annotations/${annotation.id}`,
            data: annotation
        },
        state
    );
}

export function deleteProjectAnnotationRequest(state, annotationId) {
    if (_.get(state, 'projects.layerId')) {
        return authedRequest(
            {
                method: 'delete',
                url:
                    `${state.api.apiUrl}` +
                    `/api/projects/${state.projects.projectId}` +
                    `/layers/${state.projects.layerId}/annotations/${annotationId}`
            },
            state
        );
    }
    return authedRequest(
        {
            method: 'delete',
            url:
                `${state.api.apiUrl}` +
                `/api/projects/${state.projects.projectId}/annotations/${annotationId}`
        },
        state
    );
}

export function clearProjectAnnotationsRequest(state) {
    if (_.get(state, 'projects.layerId')) {
        return authedRequest(
            {
                method: 'delete',
                url:
                    `${state.api.apiUrl}` +
                    `/api/projects/${state.projects.projectId}` +
                    `/layers/${state.projects.layerId}/annotations/`
            },
            state
        );
    }
    return authedRequest(
        {
            method: 'delete',
            url: `${state.api.apiUrl}` + `/api/projects/${state.projects.projectId}/annotations/`
        },
        state
    );
}
