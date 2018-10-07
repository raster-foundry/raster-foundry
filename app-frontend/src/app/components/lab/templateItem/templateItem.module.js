/* global BUILDCONFIG, _ */
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
    // user.id is a desperate last resort
        this.templateOwner = _.chain([
            user.personalInfo.firstName,
            user.personalInfo.lastName,
            user.name,
            user.email,
            user.id
        ]).compact().head().value();
    }
}

const TemplateItemModule = angular.module('components.lab.toolItem', []);

TemplateItemModule.component('rfTemplateItem', TemplateItemComponent);
TemplateItemModule.controller('TemplateItemController', TemplateItemController);

export default TemplateItemModule;
