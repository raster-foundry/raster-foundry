import assetLogo from '../../../assets/images/logo-raster-foundry.png';
const Map = require('es6-map');
const _ = require('lodash');

export default class BrowseController {
    constructor( // eslint-disable-line max-params
        $log, auth, $scope, sceneService, $state, $uibModal
    ) {
        'ngInject';
        this.$log = $log;
        this.auth = auth;
        this.sceneService = sceneService;
        this.$state = $state;
        this.$uibModal = $uibModal;

        this.assetLogo = assetLogo;
        this.scenes = {
            count: 0,
            results: []
        };
        this.selectedScenes = new Map();
        this.sceneList = [];
        // initial data

        this.queryParams = _.mapValues($state.params, (val) => {
            if (val === '' || typeof val === 'undefined') {
                return null;
            }
            return val;
        });

        if ($state.params.id) {
            this.sceneService.query({id: $state.params.id}).then(
                (scene) => {
                    this.openDetailPane(scene);
                },
                () => {
                    this.queryParams.id = null;
                    this.$state.go('.', this.queryParams, {notify: false});
                }
            );
        }

        this.filters = Object.assign({}, this.queryParams);
        delete this.filters.id;

        // watchers
        $scope.$on('$stateChangeStart', this.onStateChangeStart.bind(this));
        $scope.$watchCollection('$ctrl.filters', this.onFilterChange.bind(this));

        this.populateInitialSceneList();
    }

    onStateChangeStart(event, toState, toParams, fromState) {
        if (toState.name === fromState.name) {
            event.preventDefault();
            if (toParams.id === '') {
                this.closeDetailPane();
            } else {
                this.sceneService.query({id: toParams.id}).then(
                    (scene) => {
                        this.openDetailPane(scene);
                    },
                    () => {
                        this.queryParams.id = null;
                        this.$state.go(
                            '.',
                            this.queryParams,
                            {notify: false, location: 'replace'}
                        );
                    }
                );
            }
        }
    }

    onFilterChange(newFilters) {
        this.queryParams = Object.assign({
            id: this.queryParams.id,
            maxCloudCover: null,
            minCloudCover: null,
            minAcquisitionDatetime: null,
            maxAcquisitionDatetime: null,
            datasource: null,
            month: null,
            maxSunAzimuth: null,
            minSunAzimuth: null,
            maxSunElevation: null,
            minSunElevation: null,
            bbox: null,
            point: null
        }, newFilters);
        this.$state.go('.', this.queryParams, {notify: false, location: 'replace'});
        this.populateInitialSceneList();
    }

    populateInitialSceneList() {
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
        this.sceneService.query(
            Object.assign({
                sort: 'createdAt,desc',
                pageSize: '20'
            }, params)
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
            });
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

    openDetailPane(scene) {
        this.activeScene = scene;
        this.queryParams.id = scene.id;
        this.$state.go('.', this.queryParams, {notify: false, location: true});
    }

    closeDetailPane() {
        delete this.activeScene;
        this.queryParams.id = null;
        this.$state.go('.', this.queryParams, {notify: false});
    }

    toggleFilterPane() {
        this.showFilterPane = !this.showFilterPane;
    }

    selectNoScenes() {
        this.selectedScenes.clear();
    }

    toggleSelectAndClosePane() {
        this.setSelected(this.activeScene, !this.isSelected(this.activeScene));
        this.closeDetailPane();
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

    bucketModal() {
        if (!this.selectedScenes || this.selectedScenes.size === 0) {
            return;
        }

        if (this.activeModal) {
            this.activeModal.dismiss();
        }
        this.activeModal = this.$uibModal.open({
            component: 'rfBucketAddModal',
            resolve: {
                scenes: () => this.selectedScenes
            }
        });

        this.activeModal.result.then((result) => {
            if (result && result === 'scenes') {
                this.sceneModal();
            }
            delete this.activeModal;
        }, () => {
            delete this.activeModal;
        });
    }

    sceneModal() {
        if (!this.selectedScenes || this.selectedScenes.size === 0) {
            return;
        }

        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        this.activeModal = this.$uibModal.open({
            component: 'rfSelectedScenesModal',
            resolve: {
                scenes: () => this.selectedScenes
            }
        });

        this.activeModal.result.then((result) => {
            if (result === 'bucket') {
                this.bucketModal();
            } else {
                this.$log.debug('modal result: ', result, ' is not implemented yet');
            }
            delete this.activeModal;
        }, () => {
            delete this.activeModal;
        });
    }

}
