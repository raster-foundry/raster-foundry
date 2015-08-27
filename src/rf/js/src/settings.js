'use strict';

var settings = {};

function setUser(user) {
    settings.user = user;
}

function getUser() {
    return settings.user;
}

module.exports = {
    setUser: setUser,
    getUser: getUser
};
