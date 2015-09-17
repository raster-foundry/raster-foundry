'use strict';

var _ = require('underscore'),
    Backbone = require('../../shim/backbone'),
    L = require('leaflet');

var MapModel = Backbone.Model.extend({
    defaults: {
        lat: 0,
        lng: 0,
        zoom: 0
    }
});

var TabModel = Backbone.Model.extend({
    defaults: {
        activeTab: 'imports'
    }
});

var Layer = Backbone.Model.extend({
    defaults: {
        name: '',
        organization: '',
        owner: 0,
        area: 0,
        capture_end: null,
        capture_start: null,
        srid: null
    },

    initialize: function() {
        this.getLeafletLayer = _.memoize(this.getLeafletLayer);
    },

    getLeafletLayer: function() {
        return new L.TileLayer(this.get('tile_url'));
    }
});

// TODO: Paginate
var BaseLayers = Backbone.Collection.extend({
    model: Layer,

    initialize: function(models, options) {
        options = options || {};
        this.onLayerSelected = options.onLayerSelected || _.noop;
        this.onLayerDeselected = options.onLayerDeselected || _.noop;
        this.on('change:selected', this.toggleSelected);
    },

    toggleSelected: function(model) {
        if (model.get('selected')) {
            this.onLayerSelected(model);
        } else {
            this.onLayerDeselected(model);
        }
    }
});

var MyLayers = BaseLayers.extend({
    url: '/imports.json'
});

var FavoriteLayers = BaseLayers.extend({
    url: '/favorites.json'
});

var PublicLayers = BaseLayers.extend({
    url: '/catalog.json'
});

module.exports = {
    FavoriteLayers: FavoriteLayers,
    Layer: Layer,
    MapModel: MapModel,
    MyLayers: MyLayers,
    PublicLayers: PublicLayers,
    TabModel: TabModel
};
