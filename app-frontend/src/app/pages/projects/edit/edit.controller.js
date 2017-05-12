const Map = require('es6-map');

export default class ProjectsEditController {
    constructor( // eslint-disable-line max-params
        $log, $q, $state, $scope, projectService, mapService, mapUtilsService, layerService,
        datasourceService, $uibModal
    ) {
        'ngInject';
        this.$log = $log;
        this.$q = $q;
        this.$state = $state;
        this.$scope = $scope;
        this.projectService = projectService;
        this.mapUtilsService = mapUtilsService;
        this.layerService = layerService;
        this.datasourceService = datasourceService;
        this.$uibModal = $uibModal;

        this.getMap = () => mapService.getMap('edit');
    }

    $onInit() {
        this.$scope.$on('$destroy', () => {
            if (this.activeModal) {
                this.activeModal.dismiss();
            }
        });

        this.mosaicLayer = new Map();
        this.sceneLayers = new Map();
        this.projectId = this.$state.params.projectid;
        this.layers = [];
        if (!this.project) {
            if (this.projectId) {
                this.loadingProject = true;
                this.projectUpdateListeners = [];
                this.projectService.loadProject(this.projectId).then(
                    (project) => {
                        this.project = project;
                        this.fitProjectExtent();
                        this.loadingProject = false;
                        this.projectUpdateListeners.forEach((wait) => {
                            wait.resolve(project);
                        });
                    },
                    () => {
                        this.loadingProject = false;
                    }
                );
                this.getSceneList();
            } else {
                this.$state.go('projects.list');
            }
        }
    }

    waitForProject() {
        return this.$q((resolve, reject) => {
            this.projectUpdateListeners.push({resolve: resolve, reject: reject});
        });
    }

    fitProjectExtent() {
        this.getMap().then(m => {
            this.mapUtilsService.fitMapToProject(m, this.project);
        });
    }

    getSceneList() {
        this.sceneRequestState = {loading: true};
        this.sceneListQuery = this.projectService.getAllProjectScenes(
            {
                projectId: this.projectId,
                pending: false
            }
        );
        this.sceneListQuery.then(
            (allScenes) => {
                this.sceneList = allScenes;
                for (const scene of this.sceneList) {
                    let scenelayer = this.layerService.layerFromScene(scene, this.projectId);
                    // Init color correction data
                    scenelayer.getColorCorrection();
                    this.sceneLayers.set(scene.id, scenelayer);
                }
                this.layerFromProject();
                this.initColorComposites();
            },
            (error) => {
                this.sceneRequestState.errorMsg = error;
            }
        ).finally(() => {
            this.sceneRequestState.loading = false;
        });
    }

    layerFromProject() {
        let layer = this.layerService.layerFromScene(this.sceneList, this.projectId, true);
        this.mosaicLayer.set(this.projectId, layer);
        layer.getMosaicTileLayer().then((tiles) => {
            this.getMap().then((map) => {
                map.setLayer('project', tiles);
            });
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

    initColorComposites() {
        this.$q.all(
            this.sceneList.map(s => this.datasourceService.get(s.datasource))
        ).then(
            dsl => {
                this.datasources = dsl;
                this.unifiedComposites = this.unifyComposites(dsl);
            }
        );
    }

    unifyComposites(datasources) {
        let composites = datasources.map(d => d.composites);
        return composites.reduce((union, comp) => {
            return Object.keys(comp).reduce((ao, ck) => {
                if (ck in union) {
                    ao[ck] = union[ck];
                }
                return ao;
            }, {});
        }, composites[0]);
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
    }

    openExportModal() {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        this.getMap().then(m => {
            return m.map.getZoom();
        }).then(zoom => {
            this.activeModal = this.$uibModal.open({
                component: 'rfExportModal',
                resolve: {
                    project: () => this.project,
                    zoom: () => zoom
                }
            });
        });
    }
}
