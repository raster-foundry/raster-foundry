export default class SceneItemController {
    constructor($scope, $attrs, thumbnailService, mapService, datasourceService) {
        'ngInject';
        this.thumbnailService = thumbnailService;
        this.mapService = mapService;
        this.isSelectable = $attrs.hasOwnProperty('selectable');
        this.isDraggable = $attrs.hasOwnProperty('draggable');
        this.isEditable = $attrs.hasOwnProperty('editable');
        this.datasourceService = datasourceService;
        this.$scope = $scope;
    }

    $onInit() {
        this.datasourceLoaded = false;

        this.datasourceService.get(this.scene.datasource).then(d => {
            this.datasourceLoaded = true;
            this.datasource = d;
        });

        this.$scope.$watch(
            () => this.selected({scene: this.scene}),
            (selected) => {
                this.selectedStatus = selected;
            }
        );

        if (this.isDraggable) {
            Object.assign(this.$scope.$parent.$treeScope.$callbacks, {
                dragStart: function () {
                    this.mapService.disableFootprints = true;
                },
                dragStop: function () {
                    this.mapService.disableFootprints = false;
                }
            });
        }
    }

    toggleSelected(event) {
        this.onSelect({scene: this.scene, selected: !this.selectedStatus});
        if (event) {
            event.stopPropagation();
        }
    }

    onAction(event) {
        this.onAction({scene: this.scene});
        event.stopPropagation();
    }

    onView(event) {
        this.onView({scene: this.scene});
        event.stopPropagation();
    }

    onDownload(event) {
        this.onDownload({scene: this.scene});
        event.stopPropagation();
    }
}
