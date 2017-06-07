const Map = require('es6-map');

export default class LabEditController {
    constructor( // eslint-disable-line max-params
        $scope, $state, $uibModal, mapService, layerService, projectService, mapUtilsService) {
        'ngInject';
        this.$scope = $scope;
        this.$parent = $scope.$parent.$ctrl;
        this.$state = $state;
        this.$uibModal = $uibModal;
        this.mapService = mapService;
        this.layerService = layerService;
        this.projectService = projectService;
        this.mapUtilsService = mapUtilsService;
        this.inputs = {
            bands: {
                nir: '5',
                red: '4'
            }
        };
    }

    $onInit() {
        this.setTestingTools();
        this.getMap = () => this.mapService.getMap('lab-run');
        this.projectId = this.$state.params.projectid;
        this.sceneLayers = new Map();
        if (this.project) {
            this.fitProjectExtent();
        } else if (this.$parent.toolId && !this.project) {
            this.ensureProjectSelected();
        }
    }

    ensureProjectSelected() {
        if (this.projectId) {
            this.loadingProject = true;
            this.projectService.query({id: this.projectId}).then(
                (project) => {
                    this.project = project;
                    this.fitProjectExtent();
                    this.loadingProject = false;
                    this.getSceneList();
                },
                () => {
                    this.loadingProject = false;
                    // @TODO: handle displaying an error message
                }
            );
        } else {
            this.selectProjectModal();
        }
    }

    fitProjectExtent() {
        this.getMap().then(m => {
            this.mapUtilsService.fitMapToProject(m, this.project);
        });
    }

    getSceneList() {
        this.sceneRequestState = {loading: true};
        this.projectService.getAllProjectScenes(
            {projectId: this.projectId}
        ).then(
            (allScenes) => {
                this.sceneList = allScenes;
                this.fitProjectExtent();
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
        for (const scene of this.sceneList) {
            let sceneLayer = this.layerService.layerFromScene(scene, this.projectId);
            this.sceneLayers.set(scene.id, sceneLayer);
        }

        this.layers = this.sceneLayers.values();
        this.getMap().then((map) => {
            map.deleteLayers('scenes');
            for (let layer of this.layers) {
                let tiles = layer.getNDVILayer([this.inputs.bands.nir, this.inputs.bands.red]);
                map.addLayer('scenes', tiles);
            }
        });
    }

    updateInputs() {
        this.layersFromScenes();
    }

    selectToolModal() {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        const backdrop = this.project ? true : 'static';
        const keyboard = Boolean(this.project);

        this.activeModal = this.$uibModal.open({
            component: 'rfProjectSelectModal',
            backdrop: backdrop,
            keyboard: keyboard,
            resolve: {
                project: () => this.project,
                content: () => ({
                    title: 'Select a project'
                })
            }
        });
    }

    selectProjectModal() {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        const backdrop = this.project ? true : 'static';
        const keyboard = Boolean(this.project);

        this.activeModal = this.$uibModal.open({
            component: 'rfProjectSelectModal',
            backdrop: backdrop,
            keyboard: keyboard,
            resolve: {
                project: () => this.project,
                content: () => ({
                    title: 'Select a project'
                })
            }
        });
    }

    setTestingTools() {
        this.tools = [
            {
                definition: 'ndvi',
                params: ['red', 'nir'],
                result: {
                    apply: '/',
                    args: [
                        {
                            apply: '-',
                            args: ['red', 'nir']
                        }, {
                            apply: '+',
                            args: ['red', 'nir']
                        }
                    ]
                }
            }, {
                definition: 'multiband_ndvi',
                params: ['LC8'],
                result: {
                    apply: '/',
                    args: [
                        {
                            apply: '-',
                            args: ['LC8[4]', 'LC8[5]']
                        }, {
                            apply: '+',
                            args: ['LC8[4]', 'LC8[5]']
                        }
                    ]
                }
            }, {
                definition: 'LC8_ndvi',
                params: ['LC8'],
                include: [
                    {
                        definition: 'ndvi',
                        params: ['red', 'nir'],
                        result: {
                            apply: '/',
                            args: [
                                {
                                    apply: '-',
                                    args: ['red', 'nir']
                                }, {
                                    apply: '+',
                                    args: ['red', 'nir']
                                }
                            ]
                        }
                    }
                ],
                result: {
                    apply: 'ndvi',
                    args: ['LC8[4]', 'LC8[5]']
                }
            }
        ];
        this.selectedTool = this.tools[0];
    }
}
