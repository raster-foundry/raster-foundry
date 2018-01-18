import angular from 'angular';
import sceneTpl from './sceneItem.html';

const SceneItemComponent = {
    templateUrl: sceneTpl,
    transclude: true,
    controller: 'SceneItemController',
    bindings: {
        scene: '<',
        actions: '<',
        onView: '&',
        onDownload: '&',

        selected: '<?',
        onSelect: '&?',
        isDisabled: '<?',
        apiSource: '<?',
        planetKey: '<?',
        onPassPlanetThumbnail: '&?'
    }
};

class SceneItemController {
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

        if (!this.apiSource || this.apiSource === 'Raster Foundry') {
            this.datasourceService.get(this.scene.datasource).then(d => {
                this.datasourceLoaded = true;
                this.datasource = d;
            });
        } else if (this.apiSource === 'Planet Labs') {
            this.getPlanetThumbnail();
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

const SceneItemModule = angular.module('components.scenes.sceneItem', []);

SceneItemModule.component('rfSceneItem', SceneItemComponent);
SceneItemModule.controller('SceneItemController', SceneItemController);

export default SceneItemModule;
