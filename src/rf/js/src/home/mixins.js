'use strict';

var React = require('react');

// React component which listens for changes to the layer collections on all
// three tabs in the Library and also provides helper methods for getting
// and setting the currently active layer.
function LayersMixin() {
    return {
        mixins: [
            React.BackboneMixin('myLayers', 'change:active'),
            React.BackboneMixin('myLayers', 'change:active_image'),
            React.BackboneMixin('favoriteLayers', 'change:active'),
            React.BackboneMixin('favoriteLayers', 'change:active_image'),
            React.BackboneMixin('publicLayers', 'change:active'),
            React.BackboneMixin('publicLayers', 'change:active_image')
        ],

        getActiveLayer: function() {
            return this.props.myLayers.getActiveLayer() ||
               this.props.favoriteLayers.getActiveLayer() ||
               this.props.publicLayers.getActiveLayer();
        },

        setActiveImage: function(id) {
            this.props.myLayers.setActiveImage(id);
            this.props.favoriteLayers.setActiveImage(id);
            this.props.publicLayers.setActiveImage(id);
        },

        hideActiveLayer: function() {
            this.props.myLayers.setActiveLayer(null);
            this.props.favoriteLayers.setActiveLayer(null);
            this.props.publicLayers.setActiveLayer(null);
        },

        hideActiveImage: function() {
            this.props.myLayers.setActiveImage(null);
            this.props.favoriteLayers.setActiveImage(null);
            this.props.publicLayers.setActiveImage(null);
        }
    };
}

var SlideInMixin = {
    ANIMATION_DURATION: 400,

    getInitialState: function() {
        return {
            slide: 'slideInLeft'
        };
    },

    close: function() {
        var self = this;
        this.setState({slide: 'slideOutLeft'});
        setTimeout(function() {
            // Consumers must implement this method!
            self.onClose();
            // Reset the state for the next time the UI component opens.
            self.setState({slide: 'slideInLeft'});
        }, this.ANIMATION_DURATION);
    }
};

module.exports = {
    LayersMixin: LayersMixin,
    SlideInMixin: SlideInMixin
};
