import angular from 'angular';
import ConfirmationModalComponent from './confirmationModal.component.js';

const ConfirmationModalModule = angular.module('components.common.confirmationModal', []);

ConfirmationModalModule.component(
    'rfConfirmationModal', ConfirmationModalComponent
);

export default ConfirmationModalModule;
