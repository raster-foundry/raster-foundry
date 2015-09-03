'use strict';

var router = require('./router').router,
    HomeController = require('./home/controllers'),
    UserController = require('./user/controllers');

router.addRoute(/^$/, HomeController, 'index');
router.addRoute(/^login/, UserController, 'login');
router.addRoute(/^sign-up/, UserController, 'signUp');
router.addRoute(/^send-activation/, UserController, 'sendActivation');
router.addRoute(/^forgot/, UserController, 'forgot');
router.addRoute(/^logout/, UserController, 'logout');
router.addRoute(/^activate/, UserController, 'activate');
