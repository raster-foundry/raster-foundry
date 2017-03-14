import TokensController from './tokens.controller.js';
require('./tokens.scss');

const TokensModule = angular.module('pages.settings.tokens', []);

TokensModule.controller('TokensController', TokensController);

export default TokensModule;
