import angular from 'angular';
import MarketSearchController from './search.controller.js';

const MarketSearchModule = angular.module('pages.market.search', []);

MarketSearchModule.controller('MarketSearchController', MarketSearchController);

export default MarketSearchModule;
