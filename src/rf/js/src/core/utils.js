'use strict';

function asset(relPath) {
    return '/static/' + relPath;
}

/**
 * Given a URL with GET query params, return an object of query parameters
 * mapped to values.
 * http://example.com?test=success&ok=true => {test: 'sucecess', ok: 'true'}
 */
function parseQueryData(url) {
    var data = url.split('?');
    return JSON.parse('{"' + decodeURI(data[1]).replace(/"/g, '\\"').replace(/&/g, '","').replace(/=/g,'":"') + '"}');
}

module.exports = {
    asset: asset,
    parseQueryData: parseQueryData
};
