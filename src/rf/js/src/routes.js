'use strict';

var router = require('./router').router,
    HomeController = require('./home/controllers').HomeController;

router.addRoute(/^/, HomeController, 'index');
