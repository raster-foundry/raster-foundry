'use strict';

require('bootstrap-tagsinput');

var defaultSettings = {},
    settings = (function() {
        return window.clientSettings ? window.clientSettings : defaultSettings;
    })();

function get(key) {
    return settings[key];
}

function setUser(user) {
    settings.user = user;
}

function getUser() {
    return settings.user;
}

module.exports = {
    setUser: setUser,
    getUser: getUser,
    get: get
};
