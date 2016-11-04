import angular from 'angular';
import MarketController from './market.controller.js';

const MarketModule = angular.module('pages.market', []);

MarketModule.controller('MarketController', MarketController);

export default MarketModule;
