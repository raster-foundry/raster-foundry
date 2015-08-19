'use strict';

var router = require('./router').router,
    HomeController = require('./home/controllers').HomeController,
    UserController = require('./home/controllers').UserController;

router.addRoute(/^/, HomeController, 'index');
router.addRoute(/^login/, UserController, 'login');
router.addRoute(/^logout/, UserController, 'logout');
