'use strict';

var Backbone = require('../../shim/backbone');

var Layer = Backbone.Model.extend({
    defaults: {
        name: '',
        organization: '',
        owner: 0,
        area: 0,
        capture_end: null,
        capture_start: null,
        srid: null
    }
});

// TODO: Paginate
var BaseLayers = Backbone.Collection.extend({
});

var MyLayers = BaseLayers.extend({
    url: '/layers.json'
});

var FavoriteLayers = BaseLayers.extend({
    url: '/favorites.json'
});

var PublicLayers = BaseLayers.extend({
    url: '/all/layers.json'
});

module.exports = {
    FavoriteLayers: FavoriteLayers,
    Layer: Layer,
    MyLayers: MyLayers,
    PublicLayers: PublicLayers
};
