/* global BUILDCONFIG */
import angular from 'angular';
import templateItemTpl from './templateItem.html';
const TemplateItemComponent = {
    templateUrl: templateItemTpl,
    controller: 'TemplateItemController',
    bindings: {
        templateData: '<'
    }
};

class TemplateItemController {
    constructor() {
        'ngInject';
        this.BUILDCONFIG = BUILDCONFIG;
    }
}

const TemplateItemModule = angular.module('components.lab.toolItem', []);

TemplateItemModule.component('rfTemplateItem', TemplateItemComponent);
TemplateItemModule.controller('TemplateItemController', TemplateItemController);

export default TemplateItemModule;
