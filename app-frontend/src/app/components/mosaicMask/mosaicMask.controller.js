export default class MosaicMaskController {
    constructor( // eslint-disable-line max-params
        $log, $scope, $q, projectService, layerService, $state, $stateParams
    ) {
        'ngInject';
        this.projectService = projectService;
        this.layerService = layerService;
        this.$state = $state;
        this.$q = $q;
        this.$log = $log;
        this.$scope = $scope;
        this.$stateParams = $stateParams;
    }

    $onInit() {
        this.project = this.$state.params.project;
        this.projectid = this.$state.params.projectid;
        this.maskList = [];

        this.scene = this.$stateParams.scene;
        if (!this.scene) {
            // temporary
            this.$state.go('editor.project.mosaic.scenes');
            // fetch scene info
        }

        this.opacity = {
            model: 100,
            options: {
                floor: 0,
                ceil: 100,
                step: 1,
                showTicks: 10,
                showTicksValues: true
            }
        };

        // fetch scene masks

        // fetch project / scene list. probably not necessary, remove later if so.
        if (!this.project) {
            if (this.projectid) {
                this.loading = true;
                this.projectService.query({id: this.projectid}).then(
                    (project) => {
                        this.project = project;
                        this.loading = false;
                        this.populateSceneList();
                    },
                    () => {
                        this.$state.go('library.projects.list');
                    });
            } else {
                this.$state.go('library.projects.list');
            }
        } else {
            this.populateSceneList();
        }

        this.$scope.$watchCollection('$ctrl.drawnPolygons', (polygons) => {
            this.maskList = polygons.map((polygon) => {
                return {
                    area: polygon.properties.area,
                    createdAt: polygon.properties.createdAt,
                    numPoints: polygon.geometry.coordinates[0].length - 1
                };
            });
        });
    }

    closePanel() {
        this.$state.go(
            'editor.project.mosaic.scenes',
            {
                projectid: this.projectid,
                project: this.project,
                sceneList: this.sceneList
            }
        );
    }

    enableDrawToolbar() {
        this.allowDrawing = !this.allowDrawing;
    }

    populateSceneList() {
        // If we are returning from a different state that might preserve the
        // sceneList, like the color correction adjustments, then we don't need
        // to re-request scenes.
        if (this.loading || this.sceneList && this.sceneList.length > 0) {
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
        this.projectService.getProjectSceneCount(this.project.id).then(
            (sceneCount) => {
                let self = this;
                // We're going to use this in a moment to create the requests for API pages
                let requestMaker = function *(totalResults, pageSize) {
                    let pageNum = 0;
                    while (pageNum * pageSize <= totalResults) {
                        yield self.projectService.getProjectScenes({
                            projectid: self.project.id,
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
                }).finally(() => this.loading = false); // eslint-disable-line no-return-assign
            }
        );
    }

    layersFromScenes() {
        this.layers = this.sceneList.map((scene) => this.layerService.layerFromScene(scene));
    }

    onDeleteMask(mask) {
        let polygonIndex = this.drawnPolygons.findIndex(
            (polygon) => polygon.properties.area === mask.area
        );
        this.drawnPolygons.splice(polygonIndex, 1);
    }
}
