const Map = require('es6-map');

export default class MosaicScenesController {
    constructor( // eslint-disable-line max-params
        $log, $scope, $q, $state, projectService, layerService
    ) {
        'ngInject';
        this.projectService = projectService;
        this.layerService = layerService;
        this.$state = $state;
        this.$q = $q;
    }

    $onInit() {
        this.project = this.$state.params.project;
        this.projectId = this.$state.params.projectid;

        this.selectedScenes = new Map();
        this.sceneList = [];

        // Populate project scenes
        if (!this.project) {
            if (this.projectId) {
                this.loading = true;
                this.projectService.query({id: this.projectId}).then(
                    (project) => {
                        this.project = project;
                        this.loading = false;
                        this.populateSceneList();
                    },
                    () => {
                        this.$state.go('library.projects.list');
                    }
                );
            } else {
                this.$state.go('library.projects.list');
            }
        } else {
            this.populateSceneList();
        }
    }

    // TODO: This and the ColorCorrectScenesController have nearly identical logic
    // here but the display logic is different; can we refactor to a general
    // scene list component with configurable display?
    populateSceneList() {
        if (this.loading || this.sceneList.length > 0) {
            this.layersFromScenes();
            return;
        }

        delete this.errorMsg;
        this.loading = true;
        // save off selected scenes so you don't lose them during the refresh
        this.sceneList = [];
        this.projectService.getAllProjectScenes({projectId: this.project.id}).then(
            (allScenes) => {
                this.sceneList = allScenes;
                this.layersFromScenes();
            },
            () => {
                this.errorMsg = 'Error loading scenes.';
            }).finally(() => {
                this.loading = false;
            }
        );
    }

    layersFromScenes() {
        this.layers = this.sceneList.map((scene) => this.layerService.layerFromScene(scene));
    }
}
