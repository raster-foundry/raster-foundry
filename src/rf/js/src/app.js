'use strict';

var $ = require('jquery'),
    Marionette = require('../shim/backbone.marionette'),
    Backbone = require('../shim/backbone'),
    router = require('./router').router,
    userModels = require('./user/models'),
    settings = require('./settings');

var App = Marionette.Application.extend({
    initialize: function() {
        this.setupUser();
        this.setupNavigation();
    },

    setupUser: function() {
        var model = new userModels.UserModel();
        if (window.user) {
            model.set(window.user);
        }
        settings.setUser(model);
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
