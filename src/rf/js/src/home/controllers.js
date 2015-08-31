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
            favItem1 = new Layer({
                name: 'Fav layer name',
                organization: 'test organization name',
                owner: 9
            }),
            favItem2 = new Layer({
                name: 'Second fav Layer',
                organization: 'Second the organization',
                owner: 1
            }),
            pubItem1 = new Layer({
                name: 'Public layer name',
                organization: 'test organization name',
                owner: 10
            }),
            pubItem2 = new Layer({
                name: 'Second public Layer',
                organization: 'Second the organization',
                owner: 10
            }),
            myLayerItems = new Backbone.Collection([layerItem1, layerItem2]),
            favoriteItems = new Backbone.Collection([favItem1, favItem2]),
            publicItems = new Backbone.Collection([pubItem1, pubItem2]),
            layers = {
                myLayerItems: myLayerItems,
                favoriteLayerItems: favoriteItems,
                publicLayerItems: publicItems
            },
            el = $('#container').get(0);

        React.render(<Library layers={layers} />, el);

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
