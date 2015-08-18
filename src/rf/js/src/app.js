'use strict';

var $ = require('jquery'),
    Marionette = require('../shim/backbone.marionette'),
    Backbone = require('../shim/backbone'),
    views = require('./core/views'),
    models = require('./core/models'),
    router = require('./router').router;

var App = Marionette.Application.extend({
    initialize: function() {
        //this.setupMap();
        this.setupNavigation();
    },

    setupMap: function() {
        this.map = new models.MapModel();

        // This view is intentionally not attached to any region.
        this._mapView = new views.MapView({
            model: this.map,
            el: '#map'
        });
    },

    setupNavigation: function() {
        $('body').on('click', '[data-url]', function(e) {
            e.preventDefault();
            router.navigate($(this).data('url'), { trigger: true });
        });
        Backbone.history.start({ pushState: true });
    }
});

module.exports = App;
