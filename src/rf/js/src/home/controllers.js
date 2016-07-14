'use strict';

var $ = require('jquery'),
    _ = require('underscore'),
    Backbone = require('../../shim/backbone'),
    React = require('react'),
    router = require('../router').router,
    settings = require('../settings'),
    Library = require('./components/library'),
    Modals = require('./components/modals'),
    models = require('../core/models');

var getProps = _.memoize(function() {
    var props = {
        tabModel: new models.TabModel(),
        mapModel: new Backbone.Model(),
        myLayers: new models.MyLayers(),
        favoriteLayers: new models.FavoriteLayers(),
        publicLayers: new models.PublicLayers(),
        pendingLayers: settings.getPendingLayers()
    };
    return props;
});

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

function libraryView(fn) {
    return loginRequired(function() {
        var el = $('#container').get(0),
            modalsEl = $('#modals').get(0),
            props = getProps();
        React.render(<Library {...props} />, el);
        React.render(<Modals {...props} />, modalsEl);
        fn.call(this, props);
    });
}

var HomeController = {
    index: function() {
        router.go('/imports');
    },

    imports: libraryView(function(props) {
        props.myLayers.fetch();
        props.tabModel.set('activeTab', 'imports');
    }),

    catalog: libraryView(function(props) {
        props.publicLayers.fetch();
        props.tabModel.set('activeTab', 'catalog');
    }),

    favorites: libraryView(function(props) {
        props.favoriteLayers.fetch();
        props.tabModel.set('activeTab', 'favorites');
    })
};

module.exports = HomeController;
