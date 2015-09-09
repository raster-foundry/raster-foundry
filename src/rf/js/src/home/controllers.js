'use strict';

var $ = require('jquery'),
    Backbone = require('../../shim/backbone'),
    React = require('react'),
    moment = require('moment'),
    router = require('../router').router,
    settings = require('../settings'),
    coreViews = require('../core/views'),
    Library = require('./components/library'),
    Modals = require('./components/modals'),
    uploads = require('../core/uploads'),
    Layer = require('../layer/models');

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

        // TODO remove these hard coded test values.
        var layerItem1 = new Layer({
                name: 'test layer name',
                organization: 'test organization name',
                owner: 1,
                area: 200,
                capture_end: moment('2015-09-02'),
                capture_start: moment('2015-09-01'),
                srid: 'A'
            }),
            layerItem2 = new Layer({
                name: 'Second the Layer',
                organization: 'Second the organization',
                owner: 1,
                area: 100,
                capture_end: moment('2015-08-20'),
                capture_start: moment('2015-08-19'),
                srid: 'B'
            }),
            favItem1 = new Layer({
                name: 'Fav layer name',
                organization: 'test organization name',
                owner: 9,
                area: 80,
                capture_end: moment('2015-08-20'),
                capture_start: moment('2015-08-19'),
                srid: 'E'
            }),
            favItem2 = new Layer({
                name: 'Second fav Layer',
                organization: 'Second the organization',
                owner: 1,
                area: 90,
                capture_end: moment('2015-09-02'),
                capture_start: moment('2015-09-01'),
                srid: 'D'
            }),
            pubItem1 = new Layer({
                name: 'Public layer name',
                organization: 'test organization name',
                owner: 10,
                area: 60,
                capture_end: moment('2015-09-02'),
                capture_start: moment('2015-09-01'),
                srid: 'C'
            }),
            pubItem2 = new Layer({
                name: 'Second public Layer',
                organization: 'Second the organization',
                owner: 10,
                area: 90,
                capture_end: moment('2015-08-20'),
                capture_start: moment('2015-08-19'),
                srid: 'B'
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

module.exports = HomeController;
