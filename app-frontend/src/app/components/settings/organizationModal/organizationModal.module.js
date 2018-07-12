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
    constructor($element, $timeout) {
        'ngInject';
        this.$element = $element;
        this.$timeout = $timeout;
    }

    $postLink() {
        this.claimFocus();
    }

    claimFocus(interval = 0) {
        this.$timeout(() => {
            const el = $(this.$element[0]).find('input').get(0);
            el.focus();
        }, interval);
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
