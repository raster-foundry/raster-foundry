const Map = require('es6-map');

export default class BucketAddModalController {
    constructor(bucketService, $log) {
        'ngInject';

        this.bucketService = bucketService;
        this.$log = $log;

        this.bucketList = [];
        this.populateBucketList(1);
        this.selectedBuckets = new Map();
    }

    populateBucketList(page) {
        if (this.loading) {
            return;
        }
        delete this.errorMsg;
        this.loading = true;
        this.bucketService.query(
            {
                sort: 'createdAt,desc',
                pageSize: 5,
                page: page - 1
            }
        ).then((bucketResult) => {
            this.lastBucketResult = bucketResult;
            this.numPaginationButtons = 6 - bucketResult.page % 5;
            if (this.numPaginationButtons < 3) {
                this.numPaginationButtons = 3;
            }
            this.currentPage = bucketResult.page + 1;
            this.bucketList = this.lastBucketResult.results;
            this.loading = false;
        }, () => {
            this.errorMsg = 'Server error.';
            this.loading = false;
        });
    }

    createNewBucket(name) {
        delete this.newBucketName;
        this.bucketService.createBucket(name).then(
            () => {
                this.populateBucketList(this.currentPage);
            },
            (err) => {
                this.$log.error('Error creating bucket:', err);
            }
        );
    }

    isSelected(bucket) {
        return this.selectedBuckets.has(bucket.id);
    }

    setSelected(bucket, selected) {
        if (selected) {
            this.selectedBuckets.set(bucket.id, bucket);
        } else {
            this.selectedBuckets.delete(bucket.id);
        }
    }

    addScenesToBuckets() {
        this.requests = new Map();
        this.selectedBuckets.forEach((bucket, bucketId) => {
            this.requests.set(bucketId, new Map());
            this.resolve.scenes.forEach((scene, sceneId) => {
                this.requests.get(bucketId).set(sceneId, false);
                this.bucketService.addScene(bucketId, sceneId).then(
                    () => {
                        this.requests.get(bucketId).set(sceneId, true);
                        if (this.allScenesAdded()) {
                            this.resolve.scenes.clear();
                            this.close();
                        }
                    },
                    (err) => {
                        this.$log.debug('error adding scene to bucket', err);
                        this.close();
                    }
                );
            });
        });
    }

    allScenesAdded() {
        let allAdded = true;
        this.requests.forEach((sceneReqs) => {
            sceneReqs.forEach((isAdded) => {
                if (!isAdded) {
                    allAdded = false;
                }
            });
        });
        return allAdded;
    }
}
