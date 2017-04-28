import ImportsController from './imports.controller.js';
require('./imports.scss');

const ImportsModule = angular.module('pages.imports', []);

ImportsModule.controller('ImportsController', ImportsController);

export default ImportsModule;
