'use strict';

var Marionette = require('../shim/backbone.marionette'),
    views = require('./core/views'),
    models = require('./core/models');

var App = new Marionette.Application({
    initialize: function() {
        this.map = new models.MapModel();

        /*
        // This view is intentionally not attached to any region.
        this._mapView = new views.MapView({
            model: this.map,
            el: '#map'
        });
        */
    }
});

module.exports = App;
