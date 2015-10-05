'use strict';

var React = require('react');

// React component which listens for changes to the layer collections on all
// three tabs in the Library and also provides helper methods for getting
// and setting the currently active layer.
function LayersMixin() {
    return {
        mixins: [
            React.BackboneMixin('myLayers', 'change:active'),
            React.BackboneMixin('favoriteLayers', 'change:active'),
            React.BackboneMixin('publicLayers', 'change:active')
        ],

        getActiveLayer: function() {
            return this.props.myLayers.getActiveLayer() ||
               this.props.favoriteLayers.getActiveLayer() ||
               this.props.publicLayers.getActiveLayer();
        },

        hideActiveLayer: function() {
            this.props.myLayers.setActiveLayer(null);
            this.props.favoriteLayers.setActiveLayer(null);
            this.props.publicLayers.setActiveLayer(null);
        }
    };
}

module.exports = {
    LayersMixin: LayersMixin
};
