'use strict';

var L = require('leaflet'),
    $ = require('jquery'),
    _ = require('underscore'),
    Marionette = require('../../shim/backbone.marionette');

// This view houses a Leaflet instance. The map container element must exist
// in the DOM before initializing.
var MapView = Marionette.ItemView.extend({
    modelEvents: {
        'change': 'updateView'
    },

    // L.Map instance.
    _leafletMap: null,

    // L.LayerGroup instance for layer tiles.
    _tilesLayerGroup: null,

    initialize: function() {
        var map = new L.Map(this.el);

        var layer = new L.TileLayer('https://cartodb-basemaps-{s}.global.ssl.fastly.net/light_nolabels/{z}/{x}/{y}.png', {
            attribution: 'Raster Foundry | Map data &copy; <a href="http://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors, &copy; <a href="http://cartodb.com/attributions">CartoDB</a>',
            maxZoom: 18
        });
        map.addLayer(layer);

        this._tilesLayerGroup = new L.LayerGroup();
        map.addLayer(this._tilesLayerGroup);

        map.addControl(new L.Control.Zoom({
            position: 'bottomright'
        }));

        map.setView([39.9500, -75.1667], 13);

        // Keep the map model up-to-date with the position of the map
        this.listenTo(map, 'moveend', this.updateMapModelPosition);
        this.listenTo(map, 'zoomend', this.updateMapModelZoom);

        this._noDataLayer = new L.TileLayer.Canvas();
        this._noDataLayer.drawTile = function(canvas, point) {
            var ctx = canvas.getContext('2d'),
                offsetX = point.y % 2 === 0 ? 128 : 0;
            ctx.font = '10px sans-serif';
            ctx.fillStyle = '#999999';
            ctx.fillText('NO DATA', 10 + offsetX, 10);
        };

        this._leafletMap = map;
    },

    onBeforeDestroy: function() {
        this._leafletMap.remove();
    },

    // Override the default render method because we manually update
    // the Leaflet map based on property changes on the map model.
    render: _.noop,

    // Update map position and zoom level.
    updateView: function() {
        var lat = this.model.get('lat'),
            lng = this.model.get('lng'),
            zoom = this.model.get('zoom');

        if (lat && lng && zoom) {
            this._leafletMap.setView([lat, lng], zoom);
        }
    },

    // Update the map model position and zoom level
    // based on the current position and zoom level
    // of the map. Do it silently so that we don't
    // get stuck in an update -> set -> update loop.
    updateMapModelPosition: function() {
        var center = this._leafletMap.getCenter();
        this.model.set({
            lat: center.lat,
            lng: center.lng
        }, { silent: true });
    },

    updateMapModelZoom: function() {
        var zoom = this._leafletMap.getZoom();
        this.model.set({
            zoom: zoom
        }, { silent: true });
    },

    addLayer: function(layer, bounds) {
        this._tilesLayerGroup.addLayer(this._noDataLayer);
        this._tilesLayerGroup.addLayer(layer);
        if (bounds) {
            this._zoomToExtentControl = new ZoomToExtentControl({
                bounds: bounds
            });
            this._leafletMap.addControl(this._zoomToExtentControl);
        }
    },

    clearLayers: function() {
        this._tilesLayerGroup.clearLayers();
        if (this._zoomToExtentControl) {
            this._leafletMap.removeControl(this._zoomToExtentControl);
            this._zoomToExtentControl = null;
        }
    },

    fitBounds: function(bounds) {
        if (bounds) {
            this._leafletMap.fitBounds(bounds);
        }
    }
});

var ZoomToExtentControl = L.Control.extend({
    options: {
        position: 'bottomright'
    },

    onAdd: function(map) {
        var self = this,
            $el = $('<div class="leaflet-control leaflet-bar"><a href="#" title="Zoom to Extent"><i class="rf-icon-zoom-in"></i></a></div>'),
            el = $el.get(0);

        this.$el = $el;
        L.DomEvent.disableClickPropagation(el);

        $el.on('click', 'a', function() {
            map.fitBounds(self.options.bounds);
        });

        return el;
    },

    onRemove: function() {
        this.$el.remove();
    }
});

module.exports = {
    MapView: MapView
};
