import { isObject } from 'lodash';
import tpl from './index.html';

class ListItemStatusController {
    constructor($log, $scope, $rootScope) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        this.$scope.$watch('$ctrl.statuses', newStatuses => {
            if (newStatuses) {
                this.updatedStatuses = newStatuses;
            }
        });
    }
}

const component = {
    bindings: {
        statusMap: '<',
        statuses: '<'
    },
    templateUrl: tpl,
    controller: ListItemStatusController.name
};

export default angular
    .module('components.common.listItemWidgets.listItemStatus', [])
    .component('rfListItemStatus', component)
    .controller(ListItemStatusController.name, ListItemStatusController)
    .name;
