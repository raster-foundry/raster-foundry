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
        this.setupPendingLayers();
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
    },

    setupPendingLayers: function() {
        var pendingLayers = settings.getPendingLayers(),
            user = settings.getUser(),
            pollInterval = 5000;

        if (user.isAuthenticated()) {
            pendingLayers.fetch();
        }

        function poll() {
            if (pendingLayers.existsUploading() ||
                pendingLayers.existsTransferring() ||
                pendingLayers.existsProcessing()) {
                pendingLayers.fetch();
            }
            setTimeout(poll, pollInterval);
        }
        setTimeout(poll, pollInterval);

        window.onbeforeunload = function() {
            if (pendingLayers.existsUploading()) {
                return 'Leaving this page will cancel uploads in progress.';
            }
        };

        return pendingLayers;
    }
});

module.exports = App;
