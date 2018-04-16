import axios from 'axios';
import {Promise} from 'es6-promise';

export function authedRequest(options, state) {
    if (!state) {
        throw new Error('authedRequest must be passed a state parameter.');
    }
    let apiState = state.api;
    if (!apiState.apiToken) {
        return Promise.reject('No API token set');
    }
    return axios(Object.assign({}, options, {
        headers: {Authorization: apiState.apiToken}
    }));
}
