'use strict';

require('./core/setup');
require('./routes');

// Initialize application.

var App = require('./app'),
    router = require('./router').router;

// Expose application so we can interact with it via JS console.
window.RF = {};
window.RF.app = new App();
window.RF.router = router;
