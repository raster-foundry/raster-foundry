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
    constructor($rootScope, $log, userService) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
        this.BUILDCONFIG = BUILDCONFIG;
    }

    $onInit() {
        this.getTemplateOwner();
    }

    getTemplateOwner() {
        if (this.templateData.owner === 'default') {
            this.templateOwner = this.BUILDCONFIG.APP_NAME;
        } else {
            this.userService.getUserById(this.templateData.owner).then(user => {
                this.setTemplateOwnerDisplay(user);
            }, err => {
                this.$log.error(err);
                this.templateOwner = this.BUILDCONFIG.APP_NAME;
            });
        }
    }

    setTemplateOwnerDisplay(user) {
        this.templateOwner = user.personalInfo.firstName.trim() &&
            user.personalInfo.lastName.trim() ?
            `${user.personalInfo.firstName.trim()} ${user.personalInfo.lastName.trim()}` :
            user.name || 'Anonymous';
    }
}

const TemplateItemModule = angular.module('components.lab.toolItem', []);

TemplateItemModule.component('rfTemplateItem', TemplateItemComponent);
TemplateItemModule.controller('TemplateItemController', TemplateItemController);

export default TemplateItemModule;
