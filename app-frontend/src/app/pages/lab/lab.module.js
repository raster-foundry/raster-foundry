import LabController from './lab.controller.js';
require('./lab.scss');

const LabModule = angular.module('pages.lab', []);

LabModule.controller('LabController', LabController);

export default LabModule;
