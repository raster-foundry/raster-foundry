import angular from 'angular';
import PublishModalComponent from './publishModal.component.js';
import PublishModalController from './publishModal.controller.js';
require('../../../assets/font/fontello/css/fontello.css');

const PublishModalModule = angular.module('components.publishModal', []);

PublishModalModule.controller(
    'PublishModalController', PublishModalController
);

PublishModalModule.component(
    'rfPublishModal', PublishModalComponent
);

export default PublishModalModule;
