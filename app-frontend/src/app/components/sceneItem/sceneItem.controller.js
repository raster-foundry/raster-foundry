export default class SceneItemController {
    constructor($scope, $attrs) {
        'ngInject';
        this.isSelectable = $attrs.hasOwnProperty('selectable');
        $scope.$watch(
            () => this.selected({scene: this.scene}),
            (selected) => {
                this.selectedStatus = selected;
            }
        );
    }

    toggleSelected(event) {
        this.onSelect({scene: this.scene, selected: !this.selectedStatus});
        event.stopPropagation();
    }
}
