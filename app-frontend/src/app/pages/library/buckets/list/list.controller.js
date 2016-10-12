class BucketsListController {
    constructor( // eslint-disable-line max-params
        $log, auth, $state, $scope, bucketService, userService
    ) {
        'ngInject';
        this.$log = $log;
        this.auth = auth;
        this.$state = $state;
        this.bucketService = bucketService;
        this.userService = userService;
        this.$scope = $scope;

        this.bucketList = [];
        this.populateBucketList($state.params.page || 1);
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
                pageSize: 10,
                page: page - 1
            }
        ).then(
            (bucketResult) => {
                this.lastBucketResult = bucketResult;
                this.numPaginationButtons = 6 - bucketResult.page % 10;
                if (this.numPaginationButtons < 3) {
                    this.numPaginationButtons = 3;
                }
                this.currentPage = bucketResult.page + 1;
                let replace = !this.$state.params.page;
                this.$state.transitionTo(
                    this.$state.$current.name,
                    {page: this.currentPage},
                    {
                        location: replace ? 'replace' : true,
                        notify: false
                    }
                );
                this.bucketList = this.lastBucketResult.results;
                this.loading = false;
                this.bucketList.forEach((bucket) => {
                    this.getBucketScenesCount(bucket);
                });
            },
            () => {
                this.errorMsg = 'Server error.';
                this.loading = false;
            }
        );
    }

    getBucketScenesCount(bucket) {
        this.bucketService.getBucketSceneCount(bucket.id).then(
            (sceneResult) => {
                let bupdate = this.bucketList.find((b) => b.id === bucket.id);
                bupdate.scenes = sceneResult.count;
            }
        );
    }

    viewBucketDetail(bucket) {
        this.$state.go('^.detail.scenes', {bucket: bucket, bucketid: bucket.id});
    }

    createNewBucket(name) {
        this.bucketService.createBucket(name).then(
            () => {
                this.populateBucketList(this.currentPage);
            },
            (err) => {
                this.$log.error('Error creating bucket:', err);
            }
        );
    }
}

export default BucketsListController;
