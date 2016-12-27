export default class SceneItemController {
    constructor($scope, $attrs, thumbnailService) {
        'ngInject';
        this.thumbnailService = thumbnailService;
        this.isSelectable = $attrs.hasOwnProperty('selectable');
        this.isDraggable = $attrs.hasOwnProperty('draggable');
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

    onAction(event) {
        this.onAction({scene: this.scene});
        event.stopPropagation();
    }
}
