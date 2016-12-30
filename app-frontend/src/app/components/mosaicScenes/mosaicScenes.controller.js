const Map = require('es6-map');

export default class MosaicScenesController {
    constructor( // eslint-disable-line max-params
        $log, $scope, $q, $state, projectService, layerService, mapService
    ) {
        'ngInject';
        this.projectService = projectService;
        this.layerService = layerService;
        this.$state = $state;
        this.$q = $q;
        this.$log = $log;
        this.mapService = mapService;
    }

    $onInit() {
        this.project = this.$state.params.project;
        this.projectId = this.$state.params.projectid;

        this.selectedScenes = new Map();

        this.onSceneMouseleave();
    }

    layersFromScenes() {
        this.layers = this.sceneList.map((scene) => this.layerService.layerFromScene(scene));
    }

    onSceneClick($event, scene) {
        $event.preventDefault();
        this.$state.go('editor.project.mosaic.mask', {scene: scene, sceneid: scene.id});
    }

    setHoveredScene(scene) {
        let toHover = this.mapService.disableFootprints ? null : scene;
        this.onSceneMouseover({scene: toHover});
    }

    removeHoveredScene() {
        this.onSceneMouseleave();
    }
}
