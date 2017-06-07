import angular from 'angular';
import RefreshTokenModalComponent from './refreshTokenModal.component.js';
import RefreshTokenModalController from './refreshTokenModal.controller.js';

const RefreshTokenModalModule = angular.module('components.settings.refreshTokenModal', []);

RefreshTokenModalModule.controller(
    'RefreshTokenModalController', RefreshTokenModalController
);
RefreshTokenModalModule.component(
    'rfRefreshTokenModal', RefreshTokenModalComponent
);

export default RefreshTokenModalModule;
