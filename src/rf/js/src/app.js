'use strict';

var $ = require('jquery'),
    Marionette = require('../shim/backbone.marionette'),
    Backbone = require('../shim/backbone'),
    router = require('./router').router,
    userModels = require('./user/models'),
    settings = require('./settings');

var App = Marionette.Application.extend({
    initialize: function() {
        settings.setUser(new userModels.UserModel());
        this.setupNavigation();
    },

    setupNavigation: function() {
        $('#container').on('click', '[data-url]', function(e) {
            e.preventDefault();
            router.navigate($(this).data('url'), { trigger: true });
        });
        Backbone.history.start({ pushState: true });
    }
});

module.exports = App;
