'use strict';

var _ = require('underscore'),
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

var STATUS_CREATED = 'created',
    STATUS_COMPLETED = 'completed',
    STATUS_FAILED = 'failed';

var Layer = Backbone.Model.extend({
    defaults: {
        name: '',
        organization: '',
        description: '',
        area: 0,
        area_unit: 0,
        capture_end: null,
        capture_start: null,
        srid: null,
        status: null,
        images: [],
        tags: [],
        thumb_small: '',
        thumb_large: '',
        active_image: false
    },

    initialize: function() {
        this.getLeafletLayer = _.memoize(this.getLeafletLayer);
    },

    getLeafletLayer: function() {
        return new L.TileLayer(this.get('tile_url'));
    },

    getActiveImage: function() {
        var id = this.get('active_image');
        return _.findWhere(this.get('images'), {id: id});
    },

    isUploading: function() {
        return this.get('status') === STATUS_CREATED;
    },

    isProcessing: function() {
        var status = this.get('status');
        return status !== STATUS_COMPLETED && status !== STATUS_FAILED;
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

    parse: function(data) {
        this.currentPage = data.current_page;
        this.pages = data.pages;
        this.prevUrl = data.prev_url;
        this.nextUrl = data.next_url;
        return data.layers;
    },

    getActiveLayer: function() {
        return this.findWhere({ active: true });
    },

    setActiveLayer: function(model) {
        var activeLayer = this.getActiveLayer();
        if (activeLayer) {
            activeLayer.set({
                active: false,
                active_image: false
            });
        }
        if (model) {
            model.set({
                active: true,
                active_image: false
            });
        }
    },

    setActiveImage: function(imageId) {
        var activeLayer = this.getActiveLayer();
        if (activeLayer) {
            activeLayer.set('active_image', imageId);
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

var PendingLayers = BaseLayers.extend({
    initialize: function() {
        this.created_at = new Date().getTime();
    },

    url: function() {
        return '/imports.json?page_size=0&pending=' + this.created_at;
    },

    existsUploading: function() {
        var uploading = this.find(function(layer) {
            return layer.isUploading();
        });
        return uploading ? true : false;
    },

    existsProcessing: function() {
        var processing = this.find(function(layer) {
            return layer.isProcessing();
        });
        return processing ? true : false;
    }
});

module.exports = {
    FavoriteLayers: FavoriteLayers,
    Layer: Layer,
    MapModel: MapModel,
    MyLayers: MyLayers,
    PublicLayers: PublicLayers,
    PendingLayers: PendingLayers,
    TabModel: TabModel
};
