export default class SceneItemController {
    constructor($scope) {
        'ngInject';
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
