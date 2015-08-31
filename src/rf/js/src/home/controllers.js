'use strict';

var $ = require('jquery'),
    Backbone = require('../../shim/backbone'),
    React = require('react'),
    router = require('../router').router,
    settings = require('../settings'),
    coreViews = require('../core/views'),
    Login = require('./components/login'),
    Library = require('./components/library'),
    Modals = require('./components/modals'),
    uploads = require('../core/uploads'),
    Layer = require('../layer/models');




var HomeController = {
    index: function() {
        // TODO remove these hard coded test values.
        var layerItem1 = new Layer({
                name: 'test layer name',
                organization: 'test organization name',
                owner: 1
            }),
            layerItem2 = new Layer({
                name: 'Second the Layer',
                organization: 'Second the organization',
                owner: 1
            }),
            layerItems = new Backbone.Collection([layerItem1, layerItem2]),
            el = $('#container').get(0),
            LibraryView = React.createFactory(Library),
            libraryView = LibraryView({collection: layerItems});

        React.render(libraryView, el);

        var fileProps = {
            handleFiles: function(e) {
                var files = e.target.files;
                uploads.uploadFiles(files);
            }
        };

        React.render(<Modals {...fileProps} />, $('#modals').get(0));

        this.mapView = new coreViews.MapView({
            el: '#map'
        });
    }
};

var UserController = {
    login: function() {
        var el = $('#container').get(0);
        React.render(<Login />, el);
    },

    logout: function() {
        settings.getUser().logout();
        router.go('/login');
    }
};

module.exports = {
    HomeController: HomeController,
    UserController: UserController
};
