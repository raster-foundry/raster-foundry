'use strict';

var React = require('react'),
    coreViews = require('../../core/views'),
    mixins = require('../mixins');

var Map = React.createBackboneClass({
    mixins: [
        mixins.LayersMixin()
    ],

    componentDidMount: function() {
        this.mapView = new coreViews.MapView({
            el: '#map'
        });
    },

    componentWillUnmount: function() {
        this.mapView.destroy();
    },

    componentDidUpdate: function() {
        var model = this.getActiveLayer();
        this.mapView.clearLayers();
        if (model) {
            this.mapView.addLayer(model.getLeafletLayer());
        }
    },

    render: function() {
        return (
            <div id="map-container">
                <div id="map"></div>
            </div>
        );
    }
});

module.exports = Map;
