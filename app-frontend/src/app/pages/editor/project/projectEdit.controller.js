const Map = require('es6-map');

export default class ProjectEditController {
    constructor( // eslint-disable-line max-params
        $scope, $rootScope, $location, $state, mapService, projectService, layerService, $uibModal
    ) {
        'ngInject';
        this.$state = $state;
        this.$scope = $scope;
        this.$location = $location;
        this.$rootScope = $rootScope;
        this.projectService = projectService;
        this.layerService = layerService;
        this.$uibModal = $uibModal;
        this.getMap = () => mapService.getMap('project');


        this.mapOptions = {
            static: false,
            fitToGeojson: false
        };
    }

    $onInit() {
        this.projectId = this.$state.params.projectid;
        this.selectedScenes = new Map();
        this.selectedLayers = new Map();
        this.mosaicLayer = new Map();
        this.sceneList = [];
        this.sceneLayers = new Map();
        this.layers = [];

        if (!this.project) {
            if (this.projectId) {
                this.loadingProject = true;
                this.projectService.query({id: this.projectId}).then(
                    (project) => {
                        this.project = project;
                        this.loadingProject = false;
                    },
                    () => {
                        this.loadingProject = false;
                        // @TODO: handle displaying an error message
                    }
                );
                this.getSceneList();
            } else {
                this.selectProjectModal();
            }
        }

        this.$scope.$watch('$ctrl.sceneList', this.fitAllScenes.bind(this));

        this.$scope.$on('$destroy', () => {
            if (this.activeModal) {
                this.activeModal.dismiss();
            }
        });
    }

    setHoveredScene(scene) {
        if (!scene) {
            return;
        }
        let styledGeojson = Object.assign({}, scene.dataFootprint, {
            properties: {
                options: {
                    weight: 2,
                    fillOpacity: 0
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
                l.getTileLayer().then((tiles) => {
                    tiles.setZIndex(this.cachedZIndices.get(id));
                });
            }
        }
    }

    fitSelectedScenes() {
        this.fitScenes(Array.from(this.selectedScenes.values()));
    }

    bringSelectedScenesToFront() {
        this.cachedZIndices = new Map();
        for (const [id, l] of this.selectedLayers) {
            l.getSceneTileLayer().then((tiles) => {
                this.cachedZIndices.set(id, tiles.options.zIndex);
                tiles.bringToFront();
            });
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
                for (const scene of this.sceneList) {
                    let scenelayer = this.layerService.layerFromScene(scene, this.projectId);
                    // Init color correction data
                    scenelayer.getColorCorrection();
                    this.sceneLayers.set(scene.id, scenelayer);
                }
                this.layerFromProject();
            },
            (error) => {
                this.sceneRequestState.errorMsg = error;
            }
        ).finally(() => {
            this.sceneRequestState.loading = false;
        });
    }

    layerFromProject() {
        this.getMap().then((map) => {
            let layer = this.layerService.layerFromScene(this.sceneList, this.projectId, true);
            this.mosaicLayer.set(this.projectId, layer);
            layer.getMosaicTileLayer().then((tiles) => {
                map.addLayer('project', tiles);
            });
        });
    }

    selectProjectModal() {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        const backdrop = this.project ? true : 'static';

        this.activeModal = this.$uibModal.open({
            component: 'rfSelectProjectModal',
            backdrop: backdrop,
            keyboard: Boolean(this.project),
            resolve: {
                project: () => this.project,
                requireSelection: () => !this.project
            }
        });

        this.activeModal.result.then(p => {
            this.$state.go(this.$state.current, {projectid: p.id});
        });

        return this.activeModal;
    }

    publishModal() {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        this.activeModal = this.$uibModal.open({
            component: 'rfPublishModal',
            resolve: {
                project: () => this.project,
                tileUrl: () => this.projectService.getProjectLayerURL(this.project),
                shareUrl: () => this.projectService.getProjectShareURL(this.project)
            }
        });

        return this.activeModal;
    }
}
