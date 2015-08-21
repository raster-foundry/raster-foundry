'use strict';

var defaultSettings = {
};

var settings = (function() {
    return window.clientSettings ? window.clientSettings : defaultSettings;
})();

function get(key) {
    return settings[key];
}

module.exports = {
    get: get
};
