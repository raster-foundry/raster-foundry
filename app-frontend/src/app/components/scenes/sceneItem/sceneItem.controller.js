export default class SceneItemController {
    constructor(
      $scope, $attrs,
      thumbnailService, mapService, datasourceService, planetLabsService) {
        'ngInject';
        this.thumbnailService = thumbnailService;
        this.mapService = mapService;
        this.isDraggable = $attrs.hasOwnProperty('draggable');
        this.datasourceService = datasourceService;
        this.$scope = $scope;
        this.planetLabsService = planetLabsService;
    }

    $onInit() {
        this.datasourceLoaded = false;

        if (this.apiSource === 'Raster Foundry') {
            this.datasourceService.get(this.scene.datasource).then(d => {
                this.datasourceLoaded = true;
                this.datasource = d;
            });
        }

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

        if (this.apiSource === 'Planet Labs') {
            this.getPlanetThumbnail();
        }
    }

    $onChanges(changes) {
        if (changes.selected && changes.selected.hasOwnProperty('currentValue')) {
            this.selectedStatus = changes.selected.currentValue;
        }
    }

    toggleSelected(event) {
        this.selectedStatus = !this.selectedStatus;
        if (this.onSelect) {
            this.onSelect({scene: this.scene, selected: this.selectedStatus});
            if (event) {
                event.stopPropagation();
            }
        }
    }

    getReferenceDate() {
        let acqDate = this.scene.filterFields.acquisitionDate;
        return acqDate ? acqDate : this.scene.createdAt;
    }

    getPlanetThumbnail() {
        this.planetLabsService.getThumbnail(
            this.planetKey, this.scene.thumbnails[0].url
        ).then(
            (thumbnail) => {
                this.planetThumbnail = thumbnail;
                this.onPassPlanetThumbnail({url: thumbnail, id: this.scene.id});
            }
        );
    }
}
