const Map = require('es6-map');

export default class ProjectEditController {
    constructor( // eslint-disable-line max-params
        $scope, $rootScope, $state, mapService, projectService, layerService
    ) {
        'ngInject';
        this.$state = $state;
        this.$scope = $scope;
        this.$rootScope = $rootScope;
        this.projectService = projectService;
        this.layerService = layerService;
        this.getMap = () => mapService.getMap('project');

        this.mapOptions = {
            static: false,
            fitToGeojson: false
        };
    }

    $onInit() {
        this.project = this.$state.params.project;
        this.projectId = this.$state.params.projectid;

        this.selectedScenes = new Map();
        this.selectedLayers = new Map();
        this.sceneList = [];
        this.sceneLayers = new Map();
        this.layers = [];

        this.$scope.$watch('$ctrl.sceneList', this.fitAllScenes.bind(this));

        this.getSceneList();
    }

    setHoveredScene(scene) {
        let styledGeojson = Object.assign({}, scene.dataFootprint, {
            properties: {
                options: {
                    style: () => {
                        return {
                            dashArray: '10, 10',
                            weight: 2,
                            fillOpacity: 0.2
                        };
                    }
                }
            }
        });
        this.getMap().then((map) => {
            map.setGeojson('footprint', styledGeojson);
        });
    }

    removeHoveredScene() {
        this.getMap().then((map) => {
            map.deleteGeojson('footprint');
        });
    }

    applyCachedZOrder() {
        if (this.cachedZIndices) {
            for (const [id, l] of this.selectedLayers) {
                l.tiles.setZIndex(this.cachedZIndices.get(id));
            }
        }
    }

    fitSelectedScenes() {
        this.fitScenes(Array.from(this.selectedScenes.values()));
    }

    bringSelectedScenesToFront() {
        this.cachedZIndices = new Map();
        for (const [id, l] of this.selectedLayers) {
            this.cachedZIndices.set(id, l.tiles.options.zIndex);
            l.tiles.bringToFront();
        }
    }

    fitAllScenes() {
        if (this.sceneList.length) {
            this.fitScenes(this.sceneList);
        }
    }

    fitScenes(scenes) {
        this.getMap().then((map) =>{
            let sceneFootprints = scenes.map((scene) => scene.dataFootprint);
            map.map.fitBounds(L.geoJSON(sceneFootprints).getBounds());
        });
    }

    getSceneList() {
        this.sceneRequestState = {loading: true};
        this.projectService.getAllProjectScenes(
            {projectId: this.projectId}
        ).then(
            (allScenes) => {
                this.sceneList = allScenes;
                this.layersFromScenes();
            },
            (error) => {
                this.sceneRequestState.errorMsg = error;
            }
        ).finally(() => {
            this.sceneRequestState.loading = false;
        });
    }

    layersFromScenes() {
        // Create scene layers to use for color correction
        for (const scene of this.sceneList) {
            let sceneLayer = this.layerService.layerFromScene(scene);
            this.sceneLayers.set(scene.id, sceneLayer);
        }
        this.layers = this.sceneLayers.values();
        this.getMap().then((map) => {
            map.deleteLayers('scenes');
            for (let layer of this.layers) {
                map.addLayer('scenes', layer.tiles);
            }
        });
    }
}
