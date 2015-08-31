'use strict';

var $ = require('jquery'),
    React = require('react'),
    router = require('../router').router,
    settings = require('../settings'),
    coreViews = require('../core/views'),
    Login = require('./components/login'),
    Library = require('./components/library'),
    Modals = require('./components/modals'),
    uploads = require('../core/uploads');




var HomeController = {
    index: function() {
        var el = $('#container').get(0);
        React.render(<Library />, el);

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
