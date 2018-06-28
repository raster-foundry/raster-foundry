import axios from 'axios';
import {Promise} from 'es6-promise';

export function authedRequest(options, state) {
    let apiState = state.api;
    if (!apiState.apiToken) {
        return Promise.reject('No API token set');
    }
    return axios(Object.assign({}, options, {
        headers: Object.assign(
            {Authorization: 'Bearer ' + apiState.apiToken},
            options.headers
        )
    }));
}
