import angular from 'angular';
import organizationModalTpl from './organizationModal.html';

const OrganizationModalComponent = {
    templateUrl: organizationModalTpl,
    controller: 'OrganizationModalController',
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    }
};

class OrganizationModalController {
    constructor() {
    }

    onAdd() {
        this.close({$value: {
            name: this.form.name.$modelValue
        }});
    }
}

const OrganizationModalModule = angular.module('components.settings.organizationModal', []);

OrganizationModalModule.component('rfOrganizationModal', OrganizationModalComponent);
OrganizationModalModule.controller('OrganizationModalController', OrganizationModalController);

export default OrganizationModalModule;
