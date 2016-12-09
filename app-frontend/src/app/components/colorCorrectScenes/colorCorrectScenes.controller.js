export default class ColorCorrectScenesController {
    constructor( // eslint-disable-line max-params
        $log, $scope, $q, projectService, layerService, $state
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

    populateSceneList() {
        // If we are returning from a different state that might preserve the
        // sceneList, like the color correction adjustments, then we don't need
        // to re-request scenes.
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
        this.projectService.getProjectSceneCount(this.project.id).then(
            (sceneCount) => {
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
                }).finally(() => this.loading = false); // eslint-disable-line no-return-assign
            }
        );
    }

    layersFromScenes() {
        // Create scene layers to use for color correction
        for (const scene of this.sceneList) {
            let sceneLayer = this.layerService.layerFromScene(scene);
            this.sceneLayers.set(scene.id, sceneLayer);
        }
        this.layers = this.sceneLayers.values();
    }

    /**
     * Set RGB bands for layers
     *
     * TODO: Only works for Landsat8 -- needs to be adjusted based on datasource
     *
     * @param {string} bandName name of band selected
     *
     * @returns {null} null
     */
    setBands(bandName) {
        let bands = {
            natural: {red: 3, green: 2, blue: 1},
            cir: {red: 4, green: 3, blue: 2},
            urban: {red: 6, green: 5, blue: 4},
            water: {red: 4, green: 5, blue: 3},
            atmosphere: {red: 6, green: 4, blue: 2},
            agriculture: {red: 5, green: 4, blue: 1},
            forestfire: {red: 6, green: 4, blue: 1},
            bareearth: {red: 5, green: 2, blue: 1},
            vegwater: {red: 4, green: 6, blue: 0}
        };

        this.sceneLayers.forEach(function (layer) {
            layer.updateBands(bands[bandName]);
        });
    }

    onToggleSelection() {
        // This fires pre-change, so if the box is checked then we need to deselect
        if (this.shouldSelectAll()) {
            this.selectAllScenes();
        } else {
            this.selectNoScenes();
        }
    }

    shouldSelectAll() {
        return this.selectedScenes.size === 0 || this.selectedScenes.size < this.sceneList.size;
    }


    selectNoScenes() {
        this.selectedScenes.clear();
        this.selectedLayers.clear();
    }

    selectAllScenes() {
        this.sceneList.map((scene) => {
            this.selectedScenes.set(scene.id, scene);
            this.selectedLayers.set(scene.id, this.sceneLayers.get(scene.id));
        });
    }

    isSelected(scene) {
        return this.selectedScenes.has(scene.id);
    }

    setSelected(scene, selected) {
        if (selected) {
            this.selectedScenes.set(scene.id, scene);
            this.selectedLayers.set(scene.id, this.sceneLayers.get(scene.id));
        } else {
            this.selectedScenes.delete(scene.id);
            this.selectedLayers.delete(scene.id);
        }
    }
}
