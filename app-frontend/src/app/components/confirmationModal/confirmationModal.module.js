import angular from 'angular';
import ConfirmationModalComponent from './confirmationModal.component.js';
require('../../../assets/font/fontello/css/fontello.css');

const ConfirmationModalModule = angular.module('components.confirmationModal', []);

ConfirmationModalModule.component(
    'rfConfirmationModal', ConfirmationModalComponent
);

export default ConfirmationModalModule;
