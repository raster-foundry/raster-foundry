const Map = require('es6-map');

export default class MosaicScenesController {
    constructor( // eslint-disable-line max-params
        $log, $scope, $q, bucketService, $state
    ) {
        'ngInject';
        this.bucketService = bucketService;
        this.$state = $state;
        this.$q = $q;
    }

    $onInit() {
        this.bucket = this.$state.params.bucket;
        this.bucketId = this.$state.params.bucketid;

        this.selectedScenes = new Map();
        this.sceneList = [];

        // Populate bucket scenes
        if (!this.bucket) {
            if (this.bucketId) {
                this.loading = true;
                this.bucketService.query({id: this.bucketId}).then(
                    (bucket) => {
                        this.bucket = bucket;
                        this.loading = false;
                        this.populateSceneList();
                    },
                    () => {
                        this.$state.go('library.buckets.list');
                    }
                );
            } else {
                this.$state.go('library.buckets.list');
            }
        } else {
            this.populateSceneList();
        }
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
        this.bucketService.getBucketScenes({
            bucketId: this.bucket.id,
            pageSize: '1'
        }).then((sceneCount) => {
            let self = this;
            // We're going to use this in a moment to create the requests for API pages
            let requestMaker = function *(totalResults, pageSize) {
                let pageNum = 0;
                while (pageNum * pageSize <= totalResults) {
                    yield self.bucketService.getBucketScenes({
                        bucketId: self.bucket.id,
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
    }

    selectAllScenes() {
        this.sceneList.map((scene) => this.selectedScenes.set(scene.id, scene));
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
}
