'use strict';

var $ = require('jquery'),
    _ = require('underscore'),
    React = require('react'),
    router = require('../router').router,
    settings = require('../settings'),
    coreViews = require('../core/views'),
    Library = require('./components/library'),
    Modals = require('./components/modals'),
    uploads = require('../core/uploads'),
    models = require('../core/models');

function loginRequired(fn) {
    return function() {
        var user = settings.getUser();
        if (!user.isAuthenticated()) {
            router.go('/login');
        } else {
            fn.apply(this, arguments);
        }
    };
}

var HomeController = {
    index: function() {
        router.go('/imports');
    },

    setupLibrary: _.memoize(function() {
        var self = this;

        var el = $('#container').get(0),
            modalsEl = $('#modals').get(0),

            opts = {
                onLayerSelected: function(model) {
                    var layer = model.getLeafletLayer();
                    self.mapView._leafletMap.addLayer(layer);
                },
                onLayerDeselected: function(model) {
                    var layer = model.getLeafletLayer();
                    self.mapView._leafletMap.removeLayer(layer);
                }
            },

            libraryProps = {
                tabModel: new models.TabModel(),
                myLayers: new models.MyLayers(null, opts),
                favoriteLayers: new models.FavoriteLayers(null, opts),
                publicLayers: new models.PublicLayers(null, opts)
            },

            fileProps = {
                handleFiles: function(e) {
                    var files = e.target.files;
                    uploads.uploadFiles(files);
                }
            };

        React.render(<Library {...libraryProps} />, el);
        React.render(<Modals {...fileProps} />, modalsEl);

        this.mapView = new coreViews.MapView({
            el: '#map'
        });

        return libraryProps;
    }),

    imports: loginRequired(function() {
        var props = this.setupLibrary();
        props.myLayers.fetch();
        props.tabModel.set('activeTab', 'imports');
    }),

    catalog: loginRequired(function() {
        var props = this.setupLibrary();
        props.publicLayers.fetch();
        props.tabModel.set('activeTab', 'catalog');
    }),

    favorites: loginRequired(function() {
        var props = this.setupLibrary();
        props.favoriteLayers.fetch();
        props.tabModel.set('activeTab', 'favorites');
    }),

    processing: loginRequired(function() {
        var props = this.setupLibrary();
        // TODO: Fetch processing tab
        props.tabModel.set('activeTab', 'processing');
    })
};



module.exports = HomeController;
