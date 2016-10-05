class ScenesListController {
    constructor($log, auth, sceneService, $state) {
        'ngInject';

        this.$log = $log;
        this.auth = auth;
        this.sceneService = sceneService;
        this.$state = $state;

        this.sceneList = [];

        this.populateSceneList($state.params.page || 1);
    }

    populateSceneList(page) {
        if (this.loading) {
            return;
        }
        delete this.errorMsg;
        this.loading = true;
        // save off selected scenes so you don't lose them during the refresh
        this.sceneList = [];
        this.sceneService.query(
            {
                sort: 'createdAt,desc',
                pageSize: '10',
                page: page - 1
            }
        ).then(function (sceneResult) {
            this.lastSceneResult = sceneResult;
            this.numPaginationButtons = 6 - sceneResult.page % 10;
            if (this.numPaginationButtons < 3) {
                this.numPaginationButtons = 3;
            }
            this.currentPage = sceneResult.page + 1;
            this.$state.transitionTo(
                this.$state.$current.name,
                {page: this.currentPage},
                {
                    location: true,
                    notify: false
                }
            );
            this.sceneList = this.lastSceneResult.results.map(function (scene) {
                return {
                    scene: scene,
                    selected: false
                };
            });
            this.loading = false;
        }.bind(this), function (error) {
            if (error.status === -1 || error.status === 500) {
                this.errorMsg = 'Server error.';
            }
            this.loading = false;
        }.bind(this));
    }
    viewSceneDetail(scene) {
        this.$state.go('library.scenes.detail', {scene: scene, id: scene.id});
    }
}

export default ScenesListController;
