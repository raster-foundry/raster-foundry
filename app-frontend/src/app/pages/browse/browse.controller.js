import assetLogo from '../../../assets/images/logo-raster-foundry.png';

class BrowseController {
    constructor( // eslint-disable-line max-params
        $log, auth, $scope, sceneService, $state
    ) {
        'ngInject';
        this.$log = $log;
        this.auth = auth;
        this.sceneService = sceneService;
        this.$state = $state;

        this.assetLogo = assetLogo;
        this.scenes = {
            count: 0,
            results: []
        };
        this.selectedScenes = [];
        this.sceneList = [];
        // initial data
        this.populateInitialSceneList();
        if ($state.params.id) {
            this.sceneService.query({id: $state.params.id}).then(
                function (scene) {
                    this.openDetailPane(scene);
                }.bind(this), function () {
                    this.$state.go('.', {id: null}, {notify: false});
                }.bind(this));
        }
    }

    populateInitialSceneList() {
        if (this.loading) {
            return;
        }

        delete this.errorMsg;
        this.loading = true;
        this.infScrollPage = 0;
        // save off selected scenes so you don't lose them during the refresh
        this.sceneList = [];
        this.sceneService.query(
            {
                sort: 'createdAt,desc',
                pageSize: '20'
            }
        ).then(function (sceneResult) {
            this.lastSceneResult = sceneResult;
            this.sceneList = sceneResult.results;
            this.loading = false;
        }.bind(this), function (error) {
            if (error.status === -1 || error.status === 500) {
                this.errorMsg = 'Server error.';
            }
            this.loading = false;
        }.bind(this));
    }

    getMoreScenes() {
        if (this.loading || !this.lastSceneResult) {
            return;
        }

        delete this.errorMsg;
        this.loading = true;
        this.infScrollPage = this.infScrollPage + 1;
        this.sceneService.query(
            {
                sort: 'createdAt,desc',
                pageSize: '20',
                page: this.infScrollPage
            }
        ).then(function (sceneResult) {
            this.lastSceneResult = sceneResult;
            let newScenes = sceneResult.results;
            this.sceneList = [...this.sceneList, ...newScenes];
            this.loading = false;
        }.bind(this), function (error) {
            if (error.status === -1 || error.status === 500) {
                this.errorMsg = 'Server error.';
            }
            this.loading = false;
        }.bind(this));
    }

    openDetailPane(scene) {
        this.hideFilterPane();
        this.activeScene = scene;
        this.$state.go('.', {id: scene.id}, {notify: false, location: true});
        this.$log.log(this.$state.params);
    }

    closeDetailPane() {
        delete this.activeScene;
        this.$state.go('.', {id: null}, {notify: false});
    }

    toggleFilterPane() {
        this.showFilterPane = !this.showFilterPane;
    }

    hideFilterPane() {
        this.showFilterPane = false;
    }

    selectAllScenes() {
        if (this.selectedScenes.length < this.sceneList.length) {
            this.sceneList.forEach((scene) => this.setSelected(scene, true));
        } else {
            this.selectNoScenes();
        }
    }

    selectNoScenes() {
        this.selectedScenes = [];
    }

    toggleSelectAndClosePane() {
        this.setSelected(this.activeScene, !this.isSelected(this.activeScene));
        this.closeDetailPane();
    }

    isSelected(scene) {
        return this.selectedScenes.indexOf(scene.id) !== -1;
    }

    setSelected(scene, selected) {
        if (!selected) {
            let index = this.selectedScenes.indexOf(scene.id);
            this.selectedScenes.splice(index, 1);
        } else if (!this.isSelected(scene)) {
            this.selectedScenes.push(scene.id);
        }
    }

}

export default BrowseController;
