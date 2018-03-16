import angular from 'angular';
import template from './dropdown.html';

const TableDropdownComponent = {
    templateUrl: template,
    controller: 'DropdownController',
    bindings: {
        options: '<'
    },
    transclude: true
};

// handle case where user click is intercepted by opening another dropdown
let openDropdownListener = null;

class DropdownController {
    constructor($document, $scope) {
        this.$document = $document;
        this.$scope = $scope;
    }

    toggleDropdown(event) {
        event.stopPropagation();
        this.open = !this.open;

        if (this.open && !this.clickListener) {
            if (openDropdownListener) {
                openDropdownListener();
            }
            const onClick = () => {
                this.open = false;
                this.$document.off('click', this.clickListener);
                this.$scope.$evalAsync();
                delete this.clickListener;
                openDropdownListener = null;
            };
            this.clickListener = onClick;
            openDropdownListener = onClick;
            this.$document.on('click', onClick);
        } else if (!this.open && this.clickListener) {
            this.$document.off('click', this.clickListener);
            openDropdownListener = null;
            delete this.clickListener;
        }
    }
}

const DropdownModule = angular.module('components.dropdown', []);

DropdownModule.component('rfDropdown', TableDropdownComponent);
DropdownModule.controller('DropdownController', DropdownController);

export default DropdownModule;
