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

export function uploadShapefileOnly(state, shapefileBuf) {
    let data = new FormData();
    data.append('name', shapefileBuf);
    if (_.get(state, 'projects.layerId')) {
        return authedRequest(
            {
                method: 'post',
                url:
                    `${state.api.apiUrl}` +
                    `/api/projects/${state.projects.projectId}` +
                    `/layers/${state.projects.layerId}/annotations/shapefile`,
                data: data,
                headers: { 'Content-Type': 'multipart/form-data;boundary="=="' }
            },
            state
        );
    }
    return authedRequest(
        {
            method: 'post',
            url:
                `${state.api.apiUrl}` +
                `/api/projects/${state.projects.projectId}/annotations/shapefile`,
            data: data,
            headers: { 'Content-Type': 'multipart/form-data;boundary="=="' }
        },
        state
    );
}

export function uploadShapefileWithProps(state, shapefileBuf, matchedKeys) {
    let data = new FormData();
    data.append('shapefile', shapefileBuf);
    data.append('label', matchedKeys.label);
    data.append('description', matchedKeys.description);
    data.append('isMachine', matchedKeys.isMachine);
    data.append('confidence', matchedKeys.confidence);
    data.append('quality', matchedKeys.quality);
    if (_.get(state, 'projects.layerId')) {
        return authedRequest(
            {
                method: 'post',
                url:
                    `${state.api.apiUrl}` +
                    `/api/projects/${state.projects.projectId}` +
                    `/layers/${state.projects.layerId}/annotations/shapefile/import`,
                data: data,
                headers: { 'Content-Type': 'multipart/form-data;boundary="=="' }
            },
            state
        );
    }
    return authedRequest(
        {
            method: 'post',
            url:
                `${state.api.apiUrl}` +
                `/api/projects/${state.projects.projectId}/annotations/shapefile/import`,
            data: data,
            headers: { 'Content-Type': 'multipart/form-data;boundary="=="' }
        },
        state
    );
}
