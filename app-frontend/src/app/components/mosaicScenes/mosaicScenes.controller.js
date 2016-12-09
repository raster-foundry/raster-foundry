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
        let params = Object.assign({}, this.queryParams);
        delete params.id;
        // Figure out how many scenes there are
        this.projectService.getProjectScenes({
            projectId: this.project.id,
            pageSize: '1'
        }).then((sceneCount) => {
            let self = this;
            // We're going to use this in a moment to create the requests for API pages
            let requestMaker = function *(totalResults, pageSize) {
                let pageNum = 0;
                while (pageNum * pageSize <= totalResults) {
                    yield self.projectService.getProjectScenes({
                        projectId: self.project.id,
                        pageSize: pageSize,
                        page: pageNum,
                        sort: 'createdAt,desc'
                    });
                    pageNum = pageNum + 1;
                }
            };
            let numScenes = sceneCount.count;
            // The default API pagesize is 30 so we'll use that.
            let pageSize = 30;
            // Generate requests for all pages
            let requests = Array.from(requestMaker(numScenes, pageSize));
            // Unpack responses into a single scene list.
            // The structure to unpack is:
            // [{ results: [{},{},...] }, { results: [{},{},...]},...]
            this.$q.all(requests).then((allResponses) => {
                this.sceneList = [].concat(...Array.from(allResponses, (resp) => resp.results));
                this.layersFromScenes();
            },
            () => {
                this.errorMsg = 'Error loading scenes.';
            }).finally(() => {
                this.loading = false;
            });
        });
    }

    layersFromScenes() {
        this.layers = this.sceneList.map((scene) => this.layerService.layerFromScene(scene));
    }
}
