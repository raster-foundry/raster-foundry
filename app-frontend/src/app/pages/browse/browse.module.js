require('./../../../assets/font/fontello/css/fontello.css');
require('./../../../assets/font/fontello/css/animation.css');
import angular from 'angular';
import ngInfiniteScroll from 'ng-infinite-scroll';

import BrowseController from './browse.controller.js';

const BrowseModule = angular.module('pages.browse', ['components.leafletMap', ngInfiniteScroll]);

BrowseModule.controller('BrowseController', BrowseController);

export default BrowseModule;
