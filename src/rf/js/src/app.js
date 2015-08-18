'use strict';

var $ = require('jquery'),
    Marionette = require('../shim/backbone.marionette'),
    Backbone = require('../shim/backbone'),
    models = require('./core/models'),
    router = require('./router').router;

var App = Marionette.Application.extend({
    initialize: function() {
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
