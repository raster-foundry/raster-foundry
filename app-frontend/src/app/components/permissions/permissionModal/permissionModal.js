import angular from 'angular';
import _ from 'lodash';
import tpl from './permissionModal.html';
import factory from '../factory';

const PermissionModalComponent = {
    templateUrl: tpl,
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    },
    controller: 'PermissionModalController'
};

class PermissionModalController {
    $onInit() {
        this.mainController = factory(
            this.resolve.object,
            this.resolve.permissionsBase,
            this.resolve.objectType,
            this.resolve.platform,
            true,
            this.close
        );
    }
}

const PermissionModalModule = angular.module('components.permissions.permissionModal', []);

PermissionModalModule.component('rfPermissionModal', PermissionModalComponent);
PermissionModalModule.controller('PermissionModalController', PermissionModalController);

export default PermissionModalModule;
