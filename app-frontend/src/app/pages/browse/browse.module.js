import BrowseController from './browse.controller.js';
require('./../../../assets/font/fontello/css/fontello.css');

const BrowseModule = angular.module('pages.browse', ['components.leafletMap']);

BrowseModule.controller('BrowseController', BrowseController);

export default BrowseModule;
