'use strict';

var $ = require('jquery'),
    React = require('react'),
    views = require('./views'),
    router = require('../router').router;

// TODO: Delete (for demonstration purposes only).
var _logged_in = false;

var HomeController = {
    index: function() {
        if (!_logged_in) {
            router.navigate('/login', {trigger: true});
            return;
        }

        var props = {
            handleLogout: function(e) {
                e.preventDefault();
                _logged_in = false;
                router.navigate('/login', {trigger: true});
            }
        };

        var el = $('#container').get(0);
        React.render(<views.LibraryView {...props} />, el);
    }
};

var UserController = {
    login: function() {
        var props = {
            handleLogin: function(e) {
                e.preventDefault();
                _logged_in = true;
                router.navigate('/', {trigger: true});
            }
        };

        var el = $('#container').get(0);
        React.render(<views.LoginView {...props} />, el);
    }
};

module.exports = {
    HomeController: HomeController,
    UserController: UserController
};
