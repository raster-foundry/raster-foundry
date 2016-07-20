import angular from 'angular';
import uirouter from 'angular-ui-router';

import routing from './app.routes';
import library from './library/library';
import login from './login/login';

require('../style/sass/main.scss');

const app = {
    template: require('./app.html'),
    controller: 'AppController',
    controllerAs: 'app'
};

class AppController {
    constructor($log) {
        'ngInject';
        this.name = 'Raster Foundry';
        $log.log('App controller loaded');
    }
}

const MODULE_NAME = 'app';

angular.module(
    MODULE_NAME,
    [
        uirouter,
        login,
        library
    ]
)
    .component('app', app)
    .controller('AppController', AppController)
    .config(routing);

export default MODULE_NAME;
