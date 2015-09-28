'use strict';

var _ = require('underscore'),
    $ = require('jquery'),
    Backbone = require('../../shim/backbone'),
    L = require('leaflet'),
    utils = require('./utils');

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
        this.on('change:favorite', this.toggleFavorite);
    },

    getLeafletLayer: function() {
        return new L.TileLayer(this.get('tile_url'));
    },

    toggleFavorite: function() {
        var method = this.get('favorite') ? 'POST' : 'DELETE';
        $.ajax({
            url: this.get('favorite_url'),
            method: method
        });
    }
});

var BaseLayers = Backbone.Collection.extend({
    model: Layer,
    currentPage: 1,
    pages: 1,
    prevUrl: null,
    nextUrl: null,

    hasPrev: function() {
        return this.prevUrl !== null;
    },

    hasNext: function() {
        return this.nextUrl !== null;
    },

    getPrevPage: function() {
        if (this.prevUrl) {
            var data = utils.parseQueryData(this.prevUrl);
            this.fetch({ data: data });
        }
    },

    getNextPage: function() {
        if (this.nextUrl) {
            var data = utils.parseQueryData(this.nextUrl);
            this.fetch({ data: data });
        }
    },

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
    },

    parse: function(data) {
        this.currentPage = data.current_page;
        this.pages = data.pages;
        this.prevUrl = data.prev_url;
        this.nextUrl = data.next_url;
        return data.layers;
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
