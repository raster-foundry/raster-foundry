import angular from 'angular';
import userModalTpl from './userModal.html';

const UserModalComponent = {
    templateUrl: userModalTpl,
    controller: 'UserModalController',
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    }
};

class UserModalController {
    constructor() {
    }

    onAdd() {
        this.close({$value: {
            name: this.form.name.$modelValue,
            email: this.form.email.$modelValue,
            role: this.form.role.$modelValue
        }});
    }
}

const UserModalModule = angular.module('components.settings.userModal', []);

UserModalModule.component('rfUserModal', UserModalComponent);
UserModalModule.controller('UserModalController', UserModalController);

export default UserModalModule;
