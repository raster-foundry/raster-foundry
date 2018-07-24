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
    constructor(
        $rootScope, $scope, $attrs, $log, $state,
        authService, modalService, datasourceService
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        this.isOwner = this.authService.getProfile().sub === this.datasource.owner;
        this.isSelectable = this.$attrs.hasOwnProperty('selectable');
        this.isGlobal = this.datasource.owner === 'default';

        this.$scope.$watch(
            () => this.selected({project: this.project}),
            (selected) => {
                this.selectedStatus = selected;
            }
        );

        this.checkSharing();
    }

    checkSharing() {
        if (this.isOwner) {
            this.datasourceService.getPermissions(this.datasource.id).then(permissions => {
                this.isShared = !!permissions.find(permission => permission.isActive);
            });
        }
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
            this.datasourceService.deleteDatasource(this.datasource.id).then(res => {
                this.$state.reload();
            }, (err) => {
                this.$log.debug('error deleting datasource', err);
            });
        });
    }
}

const DatasourceItemModule = angular.module('components.datasourceItem', []);

DatasourceItemModule.controller('DatasourceItemController', DatasourceItemController);
DatasourceItemModule.component('rfDatasourceItem', DatasourceItemComponent);

export default DatasourceItemModule;
