export default class SceneItemController {
    constructor($scope, $attrs, thumbnailService, mapService, datasourceService) {
        'ngInject';
        this.thumbnailService = thumbnailService;
        this.mapService = mapService;
        this.isDraggable = $attrs.hasOwnProperty('draggable');
        this.datasourceService = datasourceService;
        this.$scope = $scope;
    }

    $onInit() {
        this.datasourceLoaded = false;

        this.datasourceService.get(this.scene.datasource).then(d => {
            this.datasourceLoaded = true;
            this.datasource = d;
        });

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

    $onChanges(changes) {
        if (changes.selected && changes.selected.hasOwnProperty('currentValue')) {
            this.selectedStatus = changes.selected.currentValue;
        }
    }

    toggleSelected(event) {
        if (this.onSelect) {
            this.onSelect({scene: this.scene, selected: !this.selectedStatus});
            if (event) {
                event.stopPropagation();
            }
        }
    }
}
