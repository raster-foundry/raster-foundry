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
    constructor($scope, $attrs, authService) {
        'ngInject';
        let id = authService.getProfile().sub;
        this.isOwner = id === this.datasource.owner;


        this.isSelectable = $attrs.hasOwnProperty('selectable');
        $scope.$watch(
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
}

const DatasourceItemModule = angular.module('components.datasourceItem', []);

DatasourceItemModule.controller('DatasourceItemController', DatasourceItemController);
DatasourceItemModule.component('rfDatasourceItem', DatasourceItemComponent);

export default DatasourceItemModule;
