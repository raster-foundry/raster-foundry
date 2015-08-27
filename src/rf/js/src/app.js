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

    showLoginIfNeeded: function() {
        var user = settings.getUser();
        user.checkAuthentication().always(function() {
            if (!user.get('logged_in')) {
                router.go('/login/');
            }
        });
    },

    setupNavigation: function() {
        $('#container').on('click', '[data-url]', function(e) {
            e.preventDefault();
            router.navigate($(this).data('url'), { trigger: true });
        });
        Backbone.history.start({ pushState: true });

        // Do this here (as opposed to in controllers)
        // because we want to do this when the app starts
        // at any URL.
        this.showLoginIfNeeded();
    }
});

module.exports = App;
