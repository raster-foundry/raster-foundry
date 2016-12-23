import carousel from 'angular-ui-bootstrap/src/carousel';
import MarketToolController from './tool.controller.js';
require('./tool.scss');
const MarketToolModule = angular.module('pages.market.tool', [carousel]);

MarketToolModule.controller('MarketToolController', MarketToolController);

export default MarketToolModule;
