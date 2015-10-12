'use strict';

var models = require('./core/models');

require('bootstrap-tagsinput');

var defaultSettings = {},
    settings = (function() {
        return window.clientSettings ? window.clientSettings : defaultSettings;
    })(),
    pendingLayers = new models.PendingLayers();

function get(key) {
    return settings[key];
}

function setUser(user) {
    settings.user = user;
}

function getUser() {
    return settings.user;
}

function getPendingLayers() {
    return pendingLayers;
}

function getS3UriPrefix() {
    return get('awsBucketUriPrefix');
}

module.exports = {
    setUser: setUser,
    getUser: getUser,
    get: get,
    getPendingLayers: getPendingLayers,
    getS3UriPrefix: getS3UriPrefix
};
