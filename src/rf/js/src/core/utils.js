'use strict';

function asset(relPath) {
    return '/static/' + relPath;
}

function parseQueryData(url) {
    var data = url.split('?');
    return JSON.parse('{"' + decodeURI(data[1]).replace(/"/g, '\\"').replace(/&/g, '","').replace(/=/g,'":"') + '"}');
}

module.exports = {
    asset: asset,
    parseQueryData: parseQueryData
};
