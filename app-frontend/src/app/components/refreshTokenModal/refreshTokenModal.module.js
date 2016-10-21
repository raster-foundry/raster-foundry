import angular from 'angular';
import RefreshTokenModalComponent from './refreshTokenModal.component.js';
import RefreshTokenModalController from './refreshTokenModal.controller.js';
require('../../../assets/font/fontello/css/fontello.css');

const RefreshTokenModalModule = angular.module('components.refreshTokenModal', []);

RefreshTokenModalModule.controller(
    'RefreshTokenModalController', RefreshTokenModalController
);
RefreshTokenModalModule.component(
    'rfRefreshTokenModal', RefreshTokenModalComponent
);

export default RefreshTokenModalModule;
