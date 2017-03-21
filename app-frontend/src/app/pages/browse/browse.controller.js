import assetLogo from '../../../assets/images/logo-raster-foundry.png';
const Map = require('es6-map');
const _ = require('lodash');

export default class BrowseController {
    constructor(
        $log, $scope, $state, $timeout, $uibModal, sceneService, gridLayerService,
        authService, mapService, projectService, sessionStorage
    ) {
        'ngInject';

        this.$log = $log;
        this.$state = $state;
        this.$uibModal = $uibModal;
        this.$scope = $scope;
        this.$timeout = $timeout;
        this.sceneService = sceneService;
        this.authService = authService;
        this.projectService = projectService;
        this.gridLayerService = gridLayerService;
        this.sessionStorage = sessionStorage;
        this.mapService = mapService;
    }

    $onInit() {
        this.loadProject();

        this.getBrowseMap = () => this.mapService.getMap('browse');
        this.getDetailMap = () => this.mapService.getMap('detail');

        this.assetLogo = assetLogo;
        this.scenes = {
            count: 0,
            results: []
        };
        this.allSelected = false;
        this.selectedScenes = new Map();
        this.sceneList = [];
        this.gridFilterActive = false;

        this.initParams();
        this.initWatchers();
        this.initMap();
    }

    loadProject() {
        const projectId = this.$state.params.projectid;
        if (projectId) {
            this.projectService.loadProject(projectId).then(p => {
                this.project = p;
            });
        } else {
            this.$state.go('home');
        }
    }

    initParams() {
        this.queryParams = Object.assign(
            _.mapValues(this.$state.params, (val) => {
                if (val === '' || typeof val === 'undefined') {
                    return null;
                }
                return val;
            }),
            this.sessionStorage.get('filters') || {}
        );

        this.filters = Object.assign({}, this.queryParams);
        delete this.filters.id;
        delete this.filters.bbox;

        if (this.queryParams.bbox) {
            this.bounds = this.parseBBoxString(this.queryParams.bbox);
        } else {
            this.bounds = [[-30, -90], [50, 0]];
        }
    }

    initSelectedProject() {
        if (this.$state.params.projectid) {
            this.$state.go('.', this.queryParams, {notify: false});
        }
    }

    initSelectedScene() {
        if (this.$state.params.sceneid) {
            this.sceneService.query({id: this.$state.params.sceneid}).then(
                (scene) => {
                    this.openDetailPane(scene);
                },
                () => {
                    this.queryParams.sceneid = null;
                    this.$state.go('.', this.queryParams, {notify: false});
                }
            );
        }
    }

    initWatchers() {
        this.$scope.$on('$stateChangeStart', this.onStateChangeStart.bind(this));
        // TODO: Switch to one-way &-binding from child component
        this.$scope.$watchCollection('$ctrl.filters', this.onFilterChange.bind(this));
        this.$scope.$watch(
            () => {
                return this.authService.isLoggedIn;
            },
            this.onLoggedInChange.bind(this)
        );
    }

    initMap() {
        this.getBrowseMap().then((browseMap) => {
            browseMap.map.fitBounds(this.bounds);
            browseMap.on('contextmenu', ($event) => {
                $event.originalEvent.preventDefault();
                return false;
            });
            browseMap.on('movestart', () => {
                this.registerClick = false;
                return false;
            });
            browseMap.on('moveend', ($event, mapWrapper) => {
                this.$timeout(() => {
                    this.registerClick = true;
                }, 125);
                this.onViewChange(
                    mapWrapper.map.getBounds(),
                    mapWrapper.map.getCenter(),
                    mapWrapper.map.getZoom()
                );
            });
        });
    }

    onStateChangeStart(event, toState, toParams, fromState) {
        if (toState.name === fromState.name) {
            event.preventDefault();
            if (toParams.sceneid === '') {
                this.closeDetailPane();
            } else {
                this.sceneService.query({id: toParams.sceneid}).then(
                    (scene) => {
                        this.openDetailPane(scene);
                    },
                    () => {
                        this.queryParams.sceneid = null;
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
        this.sessionStorage.set('filters', this.queryParams);
        this.$state.go('.', this.queryParams, {notify: false, inherit: false, location: 'replace'});
        this.requestNewSceneList();
    }

    updateSceneGrid() {
        if (this.gridLayer) {
            this.gridLayer.updateParams(this.queryParams);
        }
    }

    // TODO: This should be refactored to use a one-way binding from the filter controller
    // rather than a scope watch.
    onFilterChange(newFilters) {
        this.queryParams = Object.assign({
            projectid: this.queryParams.projectid,
            sceneid: this.queryParams.sceneid,
            bbox: this.queryParams.bbox
        }, newFilters);
        this.onQueryParamsChange();
        this.updateSceneGrid();
    }

    onViewChange(newBounds, newCenter, zoom) {
        this.bboxCoords = newBounds.toBBoxString();
        this.zoom = zoom;
        this.center = newCenter;
        this.queryParams = Object.assign({
            sceneid: this.queryParams.sceneid,
            projectid: this.queryParams.projectId,
        }, this.filters, {bbox: this.bboxCoords});
        this.onQueryParamsChange();
    }

    onLoggedInChange(newValue) {
        if (newValue) {
            this.requestNewSceneList();

            this.gridLayer = this.gridLayerService.createNewGridLayer(
                Object.assign({}, this.queryParams)
            );
            // 100 is just a placeholder "big" number to leave plenty of space for basemaps
            this.gridLayer.setZIndex(100);
            this.gridLayer.onClick = (e, b) => {
                this.onGridClick(e, b);
            };
            this.getBrowseMap().then(browseMap => {
                browseMap.addLayer('canvasGrid', this.gridLayer);
            });
        } else if (this.gridLayer) {
            this.getBrowseMap().then(browseMap => {
                browseMap.deleteLayers('canvasGrid');
                delete this.gridLayer;
            });
        }
    }

    onGridClick(e, bbox) {
        if (!this.registerClick) {
            return;
        }
        let multi = e.evt.ctrlKey;
        if (!this.filterBboxList || !this.filterBboxList.length) {
            this.filterBboxList = [];
        }

        let filteredList = this.filterBboxList.filter(b => {
            return !b.equals(bbox) && !b.contains(bbox) && !bbox.contains(b);
        });

        if (filteredList.length === this.filterBboxList.length) {
            // If the clicked bounding box has not been selected
            if (!multi) {
                this.filterBboxList = [];
            }
            this.filterBboxList.push(bbox);
        } else if (!multi) {
            // If the clicked bounding box is already selected
            this.filterBboxList = [];
        } else {
            this.filterBboxList = filteredList;
        }

        if (!this.filterBboxLayer) {
            this.filterBboxLayer = new L.FeatureGroup();
            this.getBrowseMap().then((map) => {
                map.addLayer('filterBboxLayer', this.filterBboxLayer);
            });
        }
        this.filterBboxLayer.clearLayers();
        this.filterBboxList.forEach(b => {
            let bboxRect = L.rectangle(b, {
                fill: false
            });
            this.filterBboxLayer.addLayer(bboxRect);
        });
        this.gridFilterActive = Boolean(this.filterBboxList.length);
        this.requestNewSceneList();
    }

    getGridBboxFilterString() {
        return this.filterBboxList.map(b => b.toBBoxString()).join(';');
    }

    requestNewSceneList() {
        if (!this.queryParams.bbox && !this.gridFilterActive ||
            !this.authService.isLoggedIn ||
            this.loadingScenes &&
            // eslint-disable-next-line max-len
            _.isEqual(this.lastQueryParams, this.queryParams) && !this.gridFilterActive) {
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
        // manually set bbox parameter to selected filter bboxes
        if (this.gridFilterActive) {
            params.bbox = this.getGridBboxFilterString();
        }
        delete params.sceneid;
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
                this.allSelected = this.sceneList.every((scene) => scene.isSelected);
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

    getMoreScenes() {
        if (this.loadingScenes || !this.lastSceneResult) {
            return;
        }

        this.allSelected = false;
        delete this.errorMsg;
        this.loadingScenes = true;
        this.infScrollPage = this.infScrollPage + 1;
        let params = Object.assign({}, this.queryParams);
        // manually set bbox parameter to selected filter bboxes
        if (this.gridFilterActive) {
            params.bbox = this.getGridBboxFilterString();
        }
        delete params.sceneid;
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
        this.queryParams.sceneid = scene.id;
        this.$state.go('.', this.queryParams, {notify: false, location: true});
        this.getDetailMap().then((map) => {
            map.setThumbnail(scene);
            let sceneBounds = this.sceneService.getSceneBounds(scene);
            map.map.fitBounds(sceneBounds, {
                padding: [75, 75],
                animate: true
            });
        });
    }

    closeDetailPane() {
        delete this.activeScene;
        this.queryParams.sceneid = null;
        this.$state.go('.', this.queryParams, {notify: false});
        this.getDetailMap().then((map) => {
            map.deleteThumbnail();
        });
    }

    toggleFilterPane() {
        this.showFilterPane = !this.showFilterPane;
    }

    selectNoScenes() {
        this.selectedScenes.clear();
        this.sceneList.forEach(s => this.setSelected(s, false));
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
            this.getBrowseMap().then((map) => {
                map.setThumbnail(scene, false, true);
            });
        } else {
            this.selectedScenes.delete(scene.id);
            this.getBrowseMap().then((map) => {
                map.deleteThumbnail(scene);
            });
        }
    }

    selectAllScenes() {
        if (this.allSelected && this.sceneList.length) {
            this.sceneList.map((scene) => this.setSelected(scene, false));
        } else if (this.sceneList.length) {
            this.sceneList.map((scene) => this.setSelected(scene, true));
        }
        this.allSelected = !this.allSelected;
    }

    setHoveredScene(scene) {
        this.getBrowseMap().then((map) => {
            map.setThumbnail(scene);
        });
    }

    removeHoveredScene() {
        if (!this.activeScene) {
            this.getBrowseMap().then((map) => {
                map.deleteThumbnail();
            });
        }
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
                scenes: () => this.selectedScenes,
                selectScene: () => this.setSelected.bind(this),
                selectNoScenes: () => this.selectNoScenes.bind(this)
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
