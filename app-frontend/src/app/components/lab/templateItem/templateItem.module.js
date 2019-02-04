/* global BUILDCONFIG  */
import angular from 'angular';
import templateItemTpl from './templateItem.html';
const TemplateItemComponent = {
    templateUrl: templateItemTpl,
    controller: 'TemplateItemController',
    bindings: {
        templateData: '<',
        onTemplateDelete: '&',
        onShareClick: '&',
        hideActions: '<'
    }
};

class TemplateItemController {
    constructor($rootScope, $log, userService, analysisService, permissionsService, authService) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
        this.BUILDCONFIG = BUILDCONFIG;
    }

    $onInit() {
        this.getTemplateOwner();
        this.getTemplatePermissions(this.templateData);
    }

    getTemplatePermissions(template) {
        this.permissionsService
            .getEditableObjectPermissions('tools', 'TEMPLATE', template, this.authService.user)
            .then(permissions => {
                this.permissions = permissions.map((p) => p.actionType);
                this.showShare = this.showDelete = false;
                if (this.permissions
                    .filter(p => ['*', 'edit'].includes(p.toLowerCase()))
                    .length > 0) {
                    this.showShare = true;
                }
                if (this.permissions
                    .filter(p => ['*', 'delete'].includes(p.toLowerCase()))
                    .length > 0) {
                    this.showDelete = true;
                }
            });
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

    onClickDeleteTemplate() {
        this.onTemplateDelete({templateId: this.templateData.id});
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
