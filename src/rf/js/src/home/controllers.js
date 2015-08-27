'use strict';

var $ = require('jquery'),
    React = require('react'),
    router = require('../router').router,
    settings = require('../settings'),
    coreViews = require('../core/views'),
    Login = require('./components/login'),
    Library = require('./components/library');

var HomeController = {
    index: function() {
        var el = $('#container').get(0);
        React.render(<Library />, el);

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
