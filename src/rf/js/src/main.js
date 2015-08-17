'use strict';

require('./core/setup');
require('./routes');

// Initialize application.

var $ = require('jquery'),
    Backbone = require('../shim/backbone'),
    App = require('./app'),
    router = require('./router').router;

App.on('start', function() {
    $('body').on('click', '[data-url]', function(e) {
        e.preventDefault();
        router.navigate($(this).data('url'), { trigger: true });
    });
    Backbone.history.start({ pushState: true });
});

App.start();

// Expose application so we can interact with it via JS console.
window.RF = App;
window.RF.router = router;
