import angular from 'angular';

let login = {
    template: require('./login.html'),
    controller: 'LoginCtrl'
};

class LoginCtrl {
    constructor($log) {
        'ngInject';
        $log.log('login controller loaded');
    }
}

const MODULE_NAME = 'app.login';
angular.module(MODULE_NAME, [])
    .component('rfLogin', login)
    .controller('LoginCtrl', LoginCtrl);
export default MODULE_NAME;
