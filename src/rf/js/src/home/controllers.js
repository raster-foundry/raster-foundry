'use strict';

var $ = require('jquery'),
    React = require('react'),
    router = require('../router').router,
    settings = require('../settings'),
    coreViews = require('../core/views'),
    Library = require('./components/library'),
    Modals = require('./components/modals'),
    uploads = require('../core/uploads'),
    models = require('../layer/models');

function showLoginIfNeeded() {
    var user = settings.getUser();
    if (!user.isAuthenticated()) {
        router.go('/login');
        return true;
    }
    return false;
}

var HomeController = {
    index: function() {
        if (showLoginIfNeeded()) {
            return;
        }

        var el = $('#container').get(0),
            modalsEl = $('#modals').get(0),
            layers = {
                myLayers: new models.MyLayers(),
                favoriteLayers: new models.FavoriteLayers(),
                publicLayers: new models.PublicLayers()
            },
            fileProps = {
                handleFiles: function(e) {
                    var files = e.target.files;
                    uploads.uploadFiles(files);
                }
            };

        layers.myLayers.fetch();
        layers.favoriteLayers.fetch();
        layers.publicLayers.fetch();

        React.render(<Library {...layers} />, el);
        React.render(<Modals {...fileProps} />, modalsEl);

        this.mapView = new coreViews.MapView({
            el: '#map'
        });
    }
};

module.exports = HomeController;
