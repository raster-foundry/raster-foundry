export default class DatasourceItemController {
    constructor($scope, $attrs) {
        'ngInject';

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
