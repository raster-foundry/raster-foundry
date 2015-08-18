'use strict';

var Backbone = require('../../shim/backbone');

var MapModel = Backbone.Model.extend({
    defaults: {
        lat: 0,
        lng: 0,
        zoom: 0
    }
});

module.exports = {
    MapModel: MapModel
};
