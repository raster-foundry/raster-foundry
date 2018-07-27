import tpl from './permissionItem.html';

const PermissionItemComponent = {
    bindings: {
        currentActionTag: '<',
        actions: '<',
        onChange: '&',
        onDelete: '&'
    },
    templateUrl: tpl,
    transclude: true,
    controller: 'PermissionItemController'
};

class PermissionItemController {
    handleActionChange() {
        this.onChange({ value: this.currentActionTag });
    }
    handleDelete() {
        this.onDelete();
    }
}

export default angular
    .module('components.permissions.permissionitem', [])
    .controller('PermissionItemController', PermissionItemController)
    .component('rfPermissionItem', PermissionItemComponent);
