import assetLogo from '../../../assets/images/logo-raster-foundry.png';
const Map = require('es6-map');
const _ = require('lodash');

export default class BrowseController {
    constructor( // eslint-disable-line max-params
        $log, $scope, sceneService, gridService, authService, $state, $uibModal, mapService
    ) {
        'ngInject';
        this.$log = $log;
        this.sceneService = sceneService;
        this.authService = authService;
        this.$state = $state;
        this.$uibModal = $uibModal;
        this.gridService = gridService;
        this.getMap = () => mapService.getMap('browse');

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
        delete this.filters.bbox;

        // Default bounds; we can set these to something more meaningful later, e.g. the user's
        // most recent project's bounding box, or an IP-based geolocation. If a bbox is set in
        // the query params, always use that.
        if (this.queryParams.bbox) {
            this.bounds = this.parseBBoxString(this.queryParams.bbox);
        } else {
            this.bounds = [[-30, -90], [50, 0]];
        }

        // watchers
        $scope.$on('$stateChangeStart', this.onStateChangeStart.bind(this));
        // TODO: Switch to one-way &-binding from child component
        $scope.$watchCollection('$ctrl.filters', this.onFilterChange.bind(this));

        $scope.$watch(
            function () {
                return authService.isLoggedIn;
            },
            this.onLoggedInChange.bind(this)
        );

        this.getMap().then((browseMap) => {
            browseMap.map.fitBounds(this.bounds);
            browseMap.on('moveend', ($event, mapWrapper) => {
                this.onViewChange(mapWrapper.map.getBounds(), mapWrapper.map.getZoom());
            });
        });
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

    onQueryParamsChange() {
        this.$state.go('.', this.queryParams, {notify: false, inherit: false, location: 'replace'});
        this.requestNewSceneList();
    }

    // TODO: This should be refactored to use a one-way binding from the filter controller
    // rather than a scope watch.
    onFilterChange(newFilters) {
        this.queryParams = Object.assign({
            id: this.queryParams.id,
            bbox: this.queryParams.bbox
        }, newFilters);
        this.onQueryParamsChange();
        this.loadGrid(this.bboxCoords, this.zoom);
    }

    onViewChange(newBounds, zoom) {
        this.bboxCoords = newBounds.toBBoxString();
        this.zoom = zoom;
        this.queryParams = Object.assign({
            id: this.queryParams.id
        }, this.filters, {bbox: this.bboxCoords});
        this.onQueryParamsChange();
        this.loadGrid(this.bboxCoords, this.zoom);
    }

    onLoggedInChange(newValue) {
        if (newValue) {
            this.requestNewSceneList();
        }
    }

    requestNewSceneList() {
        if (!this.queryParams.bbox ||
            !this.authService.isLoggedIn ||
            this.loadingScenes && _.isEqual(this.lastQueryParams, this.queryParams)) {
            return;
        } else if (this.loadingScenes) {
            this.pendingSceneRequest = true;
            return;
        }

        delete this.errorMsg;
        this.sceneLoadingTime = new Date().toISOString();
        this.loadingScenes = true;
        this.infScrollPage = 0;
        this.lastQueryParams = this.queryParams;
        // save off selected scenes so you don't lose them during the refresh
        this.sceneList = [];
        let params = Object.assign({}, this.queryParams);
        delete params.id;
        this.sceneService.query(
            Object.assign({
                sort: 'createdAt,desc',
                pageSize: '20',
                maxCreateDatetime: this.sceneLoadingTime
            }, params)
        ).then(
            (sceneResult) => {
                this.lastSceneResult = sceneResult;
                this.sceneList = sceneResult.results;
                this.loadingScenes = false;
                if (this.pendingSceneRequest) {
                    this.pendingSceneRequest = false;
                    this.requestNewSceneList();
                }
            },
            () => {
                this.errorMsg = 'Error loadingScenes scenes.';
                this.loadingScenes = false;
            });
    }

    loadGrid(bbox, zoom) {
        if (!zoom || !bbox || !this.authService.isLoggedIn) {
            return;
        }
        if (this.loadingGrid && !_.isEqual(this.lastQueryParams, this.queryParams)) {
            this.pendingGridRequest = true;
            this.zoom = zoom;
            return;
        }
        this.loadingGrid = true;
        let params = Object.assign({zoom: zoom}, this.queryParams);
        delete params.id;
        this.gridService.query(
            params
        ).then(
            (gridResult) => {
                this.lastGridResult = Object.assign({}, gridResult, {
                    properties: {
                        options: {
                            fillOpacity: 0,
                            color: '#465076',
                            weight: 0.5,
                            onEachFeature: (feature, layer) => {
                                let count = feature.properties.count;
                                layer.bindTooltip(`${count}`);
                            }
                        }
                    }
                });
                this.getMap().then((map) => {
                    map.setGeojson('grid', this.lastGridResult);
                });
                this.loadingGrid = false;
                if (this.pendingGridRequest) {
                    this.pendingGridRequest = false;
                    this.loadGrid(this.queryParams.bbox, this.zoom);
                }
            },
            (err) => {
                this.loadingGrid = false;
                this.$log.debug('Error loading grid:', err);
                if (this.pendingGridRequest) {
                    this.pendingGridRequest = false;
                    this.loadGrid(this.queryParams.bbox, this.zoom);
                }
            }
        );
    }

    getMoreScenes() {
        if (this.loadingScenes || !this.lastSceneResult) {
            return;
        }

        delete this.errorMsg;
        this.loadingScenes = true;
        this.infScrollPage = this.infScrollPage + 1;
        let params = Object.assign({}, this.queryParams);
        delete params.id;
        this.sceneService.query(
            Object.assign({
                sort: 'createdAt,desc',
                pageSize: '20',
                page: this.infScrollPage,
                maxCreateDatetime: this.sceneLoadingTime
            }, params)
        ).then(
            (sceneResult) => {
                this.lastSceneResult = sceneResult;
                let newScenes = sceneResult.results;
                this.sceneList = [...this.sceneList, ...newScenes];
                this.loadingScenes = false;
            },
            () => {
                this.errorMsg = 'Error loadingScenes scenes.';
                this.loadingScenes = false;
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

    setHoveredScene(scene) {
        this.getMap().then((map) => {
            map.setGeojson('hovered', scene.dataFootprint);
        });
    }

    removeHoveredScene() {
        this.getMap().then((map) => {
            map.deleteGeojson('hovered');
        });
    }

    projectModal() {
        if (!this.selectedScenes || this.selectedScenes.size === 0) {
            return;
        }

        if (this.activeModal) {
            this.activeModal.dismiss();
        }
        this.activeModal = this.$uibModal.open({
            component: 'rfProjectAddModal',
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
            if (result === 'project') {
                this.projectModal();
            } else {
                this.$log.debug('modal result: ', result, ' is not implemented yet');
            }
            delete this.activeModal;
        }, () => {
            delete this.activeModal;
        });
    }

    /**
      * Convert a string in Leaflet bbox coordinate format ("swlng,swlat,nelng,nelat") to array
      * @param {string} bboxString The bbox coordinate string to parse
      * @return {array} lat/lon coordinates specifying bounding box corners ([[0,0], [1.0, 1.0]])
      */
    parseBBoxString(bboxString) {
        let coordsStrings = bboxString.split(',');
        let coords = _.map(coordsStrings, str => parseFloat(str));
        // Leaflet expects nested coordinate arrays
        return [
            [coords[1], coords[0]],
            [coords[3], coords[2]]
        ];
    }
}
