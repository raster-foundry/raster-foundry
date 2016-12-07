const Map = require('es6-map');
const _ = require('lodash');

export default class ProjectEditController {
    constructor( // eslint-disable-line max-params
        $log, $scope, $q, projectService, layerService, $state
    ) {
        'ngInject';
        this.$log = $log;
        this.$q = $q;
        this.projectService = projectService;
        this.layerService = layerService;
        this.$state = $state;

        this.showLayerList = true;
        this.showColorCorrect = !this.showLayerList;

        this.project = this.$state.params.project;
        this.projectId = this.$state.params.projectid;

        this.selectedScenes = new Map();
        this.sceneList = [];
        this.sceneLayers = new Map();
        this.selectedLayers = new Map();

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

        // Fake data for our histogram; this will get replaced by a service call later.
        this.red = [];
        this.green = [];
        this.blue = [];
        for (let i of [0, 100, 200, 300, 400]) {
            this.red.push({x: i, y: i});
            this.green.push({x: i, y: 400 - i});
            this.blue.push({x: i, y: i / 2.0});
        }
        this.data = [
            {
                values: this.red,
                key: 'Red channel',
                color: '#bb0000',
                area: true
            },
            {
                values: this.green,
                key: 'Green channel',
                color: '#00bb00',
                area: true
            },
            {
                values: this.blue,
                key: 'Blue channel',
                color: '#0000dd',
                area: true
            }
        ];

        this.initialCorrection = {
            red: 0,
            green: 0,
            blue: 0,
            brightness: 0,
            contrast: 0
        };

        this.resetToggle = true;
        this.resetColors();
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

    onCorrectionChange(newCorrection) {
        // Fake a data update; what we'll really want to do is push to the API
        // and store the result into this.data.
        let red = _.map(this.red, (val) => ({x: val.x, y: val.y + newCorrection.red}));
        let green = _.map(this.green, (val) => ({x: val.x, y: val.y + newCorrection.green}));
        let blue = _.map(this.blue, (val) => ({x: val.x, y: val.y + newCorrection.blue}));
        this.data = [
            {
                values: red,
                key: 'Red channel',
                color: '#bb0000',
                area: true
            },
            {
                values: green,
                key: 'Green channel',
                color: '#00bb00',
                area: true
            },
            {
                values: blue,
                key: 'Blue channel',
                color: '#0000dd',
                area: true
            }
        ];
    }

    resetColors() {
        this.selectedLayers.forEach(function (layer) {
            layer.resetTiles();
        });
    }

    populateSceneList() {
        if (this.loading) {
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
                // Create scene layers to use for color correction
                for (const scene of this.sceneList) {
                    let sceneLayer = this.layerService.layerFromScene(scene);
                    this.sceneLayers.set(scene.id, sceneLayer);
                }
                this.layers = this.sceneLayers.values();
            },
            () => {
                this.errorMsg = 'Error loading scenes.';
            }).finally(() => this.loading = false); // eslint-disable-line no-return-assign
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

    // TODO Potentially transition to UI-Router for these once we can route to components
    openColorCorrect(scene) {
        if (scene) {
            this.setSelected(scene, true);
        }
        this.showColorCorrect = true;
        this.showLayerList = false;
    }

    hideColorCorrect() {
        this.showColorCorrect = false;
        this.showLayerList = true;
    }
}
