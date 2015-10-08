'use strict';

var router = require('./router').router,
    HomeController = require('./home/controllers'),
    UserController = require('./user/controllers');

router.addRoute(/^$/, HomeController, 'index');
router.addRoute(/^imports$/, HomeController, 'imports');
router.addRoute(/^catalog$/, HomeController, 'catalog');
router.addRoute(/^favorites$/, HomeController, 'favorites');
router.addRoute(/^account/, UserController, 'account');
router.addRoute(/^keys/, UserController, 'keys');
router.addRoute(/^billing/, UserController, 'billing');
router.addRoute(/^login/, UserController, 'login');
router.addRoute(/^sign-up/, UserController, 'signUp');
router.addRoute(/^send-activation/, UserController, 'sendActivation');
router.addRoute('reset-password/:uidb64/:token(/)', UserController, 'resetPassword');
router.addRoute(/^forgot/, UserController, 'forgot');
router.addRoute(/^logout/, UserController, 'logout');
router.addRoute(/^activate/, UserController, 'activate');
