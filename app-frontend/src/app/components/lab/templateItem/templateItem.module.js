/* global BUILDCONFIG, _ */
import angular from 'angular';
import templateItemTpl from './templateItem.html';
const TemplateItemComponent = {
    templateUrl: templateItemTpl,
    controller: 'TemplateItemController',
    bindings: {
        templateData: '<',
        onTemplateDelete: '&'
    }
};

class TemplateItemController {
    constructor($rootScope, $log, userService, analysisService) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
        this.BUILDCONFIG = BUILDCONFIG;
    }

    $onInit() {
        this.getTemplateOwner();
        this.getUserTemplateActions();
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

    getUserTemplateActions() {
        this.analysisService.getTemplateActions(this.templateData.id).then(actionsArray => {
            if (_.get(_.intersection(actionsArray, ['*', 'DELETE']), 'length')) {
                this.showDelete = true;
            }
        });
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
