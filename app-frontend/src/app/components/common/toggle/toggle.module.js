import angular from 'angular';
import toggleTpl from './toggle.html';

const ToggleModule = angular.module('components.toggle', []);

const rfToggle = {
    templateUrl: toggleTpl,
    controller: 'ToggleController',
    transclude: true,
    bindings: {
        value: '<',
        onChange: '&',
        radio: '<',
        className: '@',
        customIcon: '<'
    },
    controllerAs: '$ctrl'
};

class ToggleController {
    constructor() {
        'ngInject';
    }
    $onInit() {
        this.currentValue = this.value || false;
        this.radio = this.radio || false;
    }
    $onChanges(changes) {
        if ('value' in changes) {
            this.currentValue = changes.value.currentValue;
        }
    }
    toggle($event) {
        $event.stopPropagation();
        $event.preventDefault();
        this.onChange({value: !this.currentValue});
    }
}

ToggleModule.component('rfToggle', rfToggle);
ToggleModule.controller('ToggleController', ToggleController);

export default ToggleModule;
