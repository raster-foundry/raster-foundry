import carousel from 'angular-ui-bootstrap/src/carousel';
import MarketModelController from './model.controller.js';

const MarketModelModule = angular.module('pages.market.model', [carousel]);

MarketModelModule.controller('MarketModelController', MarketModelController);

export default MarketModelModule;
