export const API_INIT = 'API_INIT';

export function initApi(payload) {
    return {
        type: API_INIT,
        payload
    };
}

export default {
    initApi
};
