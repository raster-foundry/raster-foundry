import angular from 'angular';
import datasourceItemTpl from './datasourceItem.html';

const DatasourceItemComponent = {
    templateUrl: datasourceItemTpl,
    controller: 'DatasourceItemController',
    bindings: {
        datasource: '<',
        selected: '&',
        onSelect: '&'
    }
};

class DatasourceItemController {
    constructor($rootScope, $scope, $attrs, authService, modalService) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        this.isOwner = this.authService.getProfile().sub === this.datasource.owner;
        this.isSelectable = this.$attrs.hasOwnProperty('selectable');
        this.$scope.$watch(
            () => this.selected({project: this.project}),
            (selected) => {
                this.selectedStatus = selected;
            }
        );
    }

    toggleSelected(event) {
        this.onSelect({project: this.project, selected: !this.selectedStatus});
        event.stopPropagation();
    }

    onOpenDatasourceDeleteModal() {
        this.modalService.open({
            component: 'rfDatasourceDeleteModal',
            resolve: {
                datasource: () => this.datasource
            }
        }).result.then(() => {
        });
    }
}

const DatasourceItemModule = angular.module('components.datasourceItem', []);

DatasourceItemModule.controller('DatasourceItemController', DatasourceItemController);
DatasourceItemModule.component('rfDatasourceItem', DatasourceItemComponent);

export default DatasourceItemModule;
