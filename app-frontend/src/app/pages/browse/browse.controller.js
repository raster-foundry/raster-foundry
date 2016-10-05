import assetLogo from '../../../assets/images/logo-raster-foundry.png';

class BrowseController {
    constructor($log, auth, $scope, sceneService, $state) {
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
                    let item = {scene: scene, selected: false};
                    this.openDetailPane(item);
                }.bind(this), function () {
                    this.$state.go('.', {id: null}, {notify: false});
                }.bind(this));
        }

        // $scope
        $scope.$watch(
            function () {
                return this.getNumSelected();
            }.bind(this)
            , function (newVal) {
                this.numSelected = newVal;
            }.bind(this)
        );
    }

    populateInitialSceneList() {
        if (this.loading) {
            return;
        }

        delete this.errorMsg;
        this.loading = true;
        this.infScrollPage = 0;
        // save off selected scenes so you don't lose them during the refresh
        let sceneChanges = this.getSelectedSceneChanges();
        this.selectedScenes = this.updateSelectedScenes(
            this.selectedScenes, sceneChanges
        );
        this.sceneList = [];
        this.sceneService.query(
            {
                sort: 'createdAt,desc',
                pageSize: '20'
            }
        ).then(function (sceneResult) {
            this.lastSceneResult = sceneResult;
            this.sceneList = this.lastSceneResult.results.map(function (scene) {
                return {
                    scene: scene,
                    selected: this.selectedScenes.filter(function (selected) {
                        return selected.id === scene.id;
                    }).length
                };
            }.bind(this));
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
            let newScenes = sceneResult.results.map(function (scene) {
                return {
                    scene: scene,
                    selected: this.selectedScenes.filter(function (selected) {
                        return selected.id === scene.id;
                    }).length
                };
            }.bind(this));
            this.sceneList = [...this.sceneList, ...newScenes];
            this.loading = false;
        }.bind(this), function (error) {
            if (error.status === -1 || error.status === 500) {
                this.errorMsg = 'Server error.';
            }
            this.loading = false;
        }.bind(this));
    }

    getSelectedSceneChanges() {
        return this.sceneList.filter(function (item) {
            return this.selectedScenes.filter(function (scene) {
                return scene.id === item.scene.id && scene.selected !== item.selected;
            });
        }.bind(this));
    }

    updateSelectedScenes(selectedScenes, sceneChanges) {
        let added = sceneChanges.filter(function (item) {
            return item.selected;
        }).map(function (item) {
            return item.scene;
        });
        let removedIds = sceneChanges.filter(function (item) {
            return !item.selected;
        }).map(function (item) {
            return item.scene.id;
        });
        return [
            ...selectedScenes.filter(function (scene) {
                return !removedIds.includes(scene.id);
            }),
            ...added
        ];
    }

    openDetailPane(scene) {
        this.hideFilterPane();
        this.activeScene = scene;
        this.$state.go('.', {id: scene.scene.id}, {notify: false, location: true});
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
        this.sceneList = this.sceneList.map(function (item) {
            item.selected = true;
            return item;
        });
    }

    selectNoScenes() {
        this.selectedScenes = [];
        this.sceneList = this.sceneList.map(function (item) {
            item.selected = false;
            return item;
        });
    }

    toggleSelectAndClosePane() {
        this.activeScene.selected = !this.activeScene.selected;
        this.closeDetailPane();
    }

    getNumSelected() {
        let count;
        if (this.sceneList) {
            count = this.sceneList.reduce(function (total, item) {
                return item.selected ? total + 1 : total;
            }, 0);
        }
        return count || 'None';
    }

}

export default BrowseController;
