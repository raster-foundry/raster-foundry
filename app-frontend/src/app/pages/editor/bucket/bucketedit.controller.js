const Map = require('es6-map');
const _ = require('lodash');

export default class BucketEditController {
    constructor( // eslint-disable-line max-params
        $log, $scope, bucketService, $state
    ) {
        'ngInject';
        this.$log = $log;
        this.bucketService = bucketService;
        this.$state = $state;

        this.showLayerList = true;
        this.showColorCorrect = !this.showLayerList;

        this.bucket = this.$state.params.bucket;
        this.bucketId = this.$state.params.bucketid;

        this.selectedScenes = new Map();
        this.foo = 5;

        // Populate bucket scenes
        if (!this.bucket) {
            if (this.bucketId) {
                this.loading = true;
                this.bucketService.query({id: this.bucketId}).then(
                    (bucket) => {
                        this.bucket = bucket;
                        this.loading = false;
                        this.populateSceneList($state.params.page || 1);
                    },
                    () => {
                        this.$state.go('^.^.list');
                    }
                );
            } else {
                this.$state.go('^.^.list');
            }
        } else {
            this.populateSceneList($state.params.page || 1);
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
        this.resetToggle = !this.resetToggle;
    }

    populateSceneList() {
        if (this.loading) {
            this.reloadScenes = true;
            return;
        }

        delete this.errorMsg;
        this.loading = true;
        this.infScrollPage = 0;
        // save off selected scenes so you don't lose them during the refresh
        this.sceneList = [];
        let params = Object.assign({}, this.queryParams);
        delete params.id;
        this.bucketService.getBucketScenes(
            {
                sort: 'createdAt,desc',
                pageSize: '10',
                bucketId: this.bucket.id
            }
        ).then(
            (sceneResult) => {
                this.lastSceneResult = sceneResult;
                this.sceneList = sceneResult.results;
                this.loading = false;
                if (this.reloadScenes) {
                    this.reloadScenes = false;
                    this.populateInitialSceneList();
                }
            },
            () => {
                this.errorMsg = 'Error loading scenes.';
                this.loading = false;
            }
        );
    }

    getMoreScenes() {
        if (this.loading || !this.lastSceneResult) {
            return;
        }

        delete this.errorMsg;
        this.loading = true;
        this.infScrollPage = this.infScrollPage + 1;
        let params = Object.assign({}, this.queryParams);
        delete params.id;
        this.sceneService.query(
            Object.assign({
                sort: 'createdAt,desc',
                pageSize: '20',
                page: this.infScrollPage
            }, params)
        ).then(
            (sceneResult) => {
                this.lastSceneResult = sceneResult;
                let newScenes = sceneResult.results;
                this.sceneList = [...this.sceneList, ...newScenes];
                this.loading = false;
            },
            () => {
                this.errorMsg = 'Error loading scenes.';
                this.loading = false;
            }
        );
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
        return !this.lastSceneResult || this.selectedScenes.size < this.lastSceneResult.count;
    }


    selectNoScenes() {
        this.selectedScenes.clear();
    }

    selectAllScenes() {
        _.each(this.sceneList, (scene) => this.selectedScenes.set(scene.id, scene));
    }

    isSelected(scene) {
        return this.selectedScenes.has(scene.id);
    }

    setSelected(scene, selected) {
        if (selected) {
            this.selectedScenes.set(scene.id, scene);
        } else {
            this.selectedScenes.delete(scene.id);
        }
    }

    // TODO Potentially transition to UI-Router for these once we can route to components
    openColorCorrect() {
        this.showColorCorrect = true;
        this.showLayerList = false;
    }

    hideColorCorrect() {
        this.showColorCorrect = false;
        this.showLayerList = true;
    }
}
