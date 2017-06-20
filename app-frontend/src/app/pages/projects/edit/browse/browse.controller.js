const Map = require('es6-map');
const _ = require('lodash');

export default class ProjectAddScenesBrowseController {
    constructor( // eslint-disable-line max-params
        $log, $state, $uibModal, $scope, $timeout, mapService, sceneService,
        projectService, gridLayerService, sessionStorage
    ) {
        'ngInject';
        this.mapService = mapService;
        this.$parent = $scope.$parent.$ctrl;
        this.$log = $log;
        this.$state = $state;
        this.$uibModal = $uibModal;
        this.$scope = $scope;
        this.$timeout = $timeout;
        this.sceneService = sceneService;
        this.projectService = projectService;
        this.gridLayerService = gridLayerService;
        this.sessionStorage = sessionStorage;
        this.mapService = mapService;

        this.getMap = () => this.mapService.getMap('edit');
        this.getPreviewMap = () => this.mapService.getMap('preview');
    }

    $onInit() {
        this.projectScenesReady = false;
        this.allSelected = false;
        this.registerClick = true;
        this.scenes = {
            count: 0,
            results: []
        };
        this.selectedScenes = new Map();
        this.sceneList = [];
        this.gridFilterActive = false;
        if (!this.$parent.project) {
            this.project = this.$parent.project;
            this.$parent.waitForProject().then((project) => {
                this.project = project;
                this.initParams();
                this.getProjectSceneIds();
                this.initWatchers();
                this.initMap();
            });
        } else {
            this.project = this.$parent.project;
            this.initParams();
            this.getProjectSceneIds();
            this.initWatchers();
            this.initMap();
            this.requestNewSceneList();
        }

        this.$scope.$on('$destroy', () => {
            this.getMap().then(browseMap => {
                browseMap.deleteLayers('canvasGrid');
                browseMap.deleteLayers('filterBboxLayer');
                browseMap.deleteLayers('Selected Scenes');
                browseMap.deleteThumbnail();
            });
            this.selectNoScenes();
        });
    }

    initParams() {
        const routeParams = [
            'projectid',
            'sceneid'
        ];

        const cleanedParams = _.omit(this.$state.params, routeParams) || {};
        const sessionFilters = this.sessionStorage.get('filters') || {};
        let cleanedFilters = {};

        if (sessionFilters.forProjectId === this.project.id) {
            cleanedFilters = _.omit(this.sessionStorage.get('filters'), routeParams) || {};
        }

        this.queryParams = Object.assign(
            _.mapValues(cleanedParams, (val) => val ? val : null),
            cleanedFilters
        );

        this.routeParams = _.mapValues(
            _.pick(this.$state.params, routeParams),
            (val) => val ? val : null
        );

        this.initSelectedScene();

        this.filters = Object.assign({}, this.queryParams);
        delete this.filters.bbox;
    }

    initSelectedScene() {
        if (this.$state.params.sceneid) {
            this.sceneService.query({id: this.$state.params.sceneid}).then(
                (scene) => {
                    this.openDetailPane(scene);
                },
                () => {
                    this.$state.go('.', this.getCombinedParams(), {notify: false});
                }
            );
        }
    }

    initWatchers() {
        this.$scope.$on('$stateChangeStart', this.onStateChangeStart.bind(this));
        // TODO: Switch to one-way &-binding from child component
        this.$scope.$watchCollection('$ctrl.filters', this.onFilterChange.bind(this));
    }

    initMap() {
        if (this.queryParams.bbox) {
            this.bounds = this.parseBBoxString(this.queryParams.bbox);
        } else if (this.project && this.project.extent) {
            this.bounds = L.geoJSON(this.project.extent).getBounds();
        } else {
            this.bounds = [[-30, -90], [50, 0]];
        }
        this.getMap().then(browseMap => {
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

            this.gridLayer = this.gridLayerService.createNewGridLayer(
                Object.assign({}, this.queryParams)
            );
            // 100 is just a placeholder "big" number to leave plenty of space for basemaps
            this.gridLayer.setZIndex(100);
            this.gridLayer.onClick = (e, b) => {
                this.onGridClick(e, b);
            };
            browseMap.addLayer('canvasGrid', this.gridLayer);
        });
    }

    getProjectSceneIds() {
        this.projectService.getAllProjectScenes({ projectId: this.project.id }).then((scenes) => {
            this.projectSceneIds = scenes.map(s => s.id);
            this.projectScenesReady = true;
        });
    }

    requestNewSceneList() {
        if (!this.queryParams.bbox && !this.gridFilterActive ||
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

    onStateChangeStart(event, toState, toParams, fromState) {
        if (toState.name === fromState.name) {
            if (!toParams.sceneid) {
                this.closeDetailPane();
            } else {
                // Should we be waiting on this response to open the pane?
                // Could make the UI feel sluggish, we may want to find
                // a way to avoid this
                this.sceneService.query({id: toParams.sceneid}).then(
                    (scene) => {
                        this.openDetailPane(scene);
                    },
                    () => {
                        this.routeParams.sceneid = null;
                        this.$state.go(
                            '.',
                            this.getCombinedParams(),
                            {notify: false, location: 'replace'}
                        );
                    }
                );
            }
        }
    }

    onQueryParamsChange() {
        const filterObject = Object.assign(this.queryParams, { forProjectId: this.project.id });
        this.sessionStorage.set('filters', filterObject);
        this.$state.go('.', this.getCombinedParams(), {
            notify: false,
            inherit: false,
            location: 'replace'
        });
        this.requestNewSceneList();
    }

    getCombinedParams() {
        return Object.assign(this.queryParams, this.routeParams);
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
            bbox: this.queryParams.bbox
        }, newFilters);
        this.onQueryParamsChange();
        this.updateSceneGrid();
    }

    onViewChange(newBounds, newCenter, zoom) {
        this.bboxCoords = newBounds.toBBoxString();
        this.$parent.zoom = zoom;
        this.$parent.center = newCenter;
        this.queryParams = Object.assign(this.filters, {bbox: this.bboxCoords});
        this.onQueryParamsChange();
    }

    toggleFilterPane() {
        this.showFilterPane = !this.showFilterPane;
    }

    setHoveredScene(scene) {
        if (scene !== this.hoveredScene) {
            this.hoveredScene = scene;
            this.getMap().then((map) => {
                map.setThumbnail(scene);
            });
        }
    }

    removeHoveredScene() {
        this.getMap().then((map) => {
            delete this.hoveredScene;
            map.deleteThumbnail();
        });
    }

    setSelected(scene, selected) {
        this.getMap().then((map) => {
            if (selected) {
                this.selectedScenes.set(scene.id, scene);
                map.setThumbnail(scene, false, true);
            } else {
                this.selectedScenes.delete(scene.id);
                map.deleteThumbnail(scene);
            }
        });
    }

    isSelected(scene) {
        return this.selectedScenes.has(scene.id);
    }

    selectAllScenes() {
        if (this.allSelected && this.sceneList.length) {
            this.sceneList.map((scene) => this.setSelected(scene, false));
            this.getMap().then((map) => {
                map.deleteThumbnail();
            });
        } else if (this.sceneList.length) {
            this.sceneList.map((scene) => this.setSelected(scene, true));
        }
        this.allSelected = !this.allSelected;
    }

    selectNoScenes() {
        this.selectedScenes.forEach(s => this.setSelected(s, false));
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
            this.getMap().then((map) => {
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

    sceneModal() {
        if (!this.selectedScenes || this.selectedScenes.size === 0) {
            return;
        }

        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        this.activeModal = this.$uibModal.open({
            component: 'rfProjectAddScenesModal',
            resolve: {
                scenes: () => this.selectedScenes,
                selectScene: () => this.setSelected.bind(this),
                selectNoScenes: () => this.selectNoScenes.bind(this),
                project: () => this.project
            }
        });

        this.activeModal.result.then(sceneIds => {
            this.projectSceneIds = this.projectSceneIds.concat(sceneIds);
            this.selectNoScenes();
        }).finally(() => {
            delete this.activeModal;
            this.$parent.getSceneList();
        });
    }

    openDetailPane(scene) {
        this.activeScene = scene;
        this.$parent.showPreviewMap = true;
        this.routeParams.sceneid = scene.id;
        this.$state.go('.', this.getCombinedParams(), {notify: false, location: true});
        this.getPreviewMap().then((previewMap) => {
            previewMap.setThumbnail(scene);
            let sceneBounds = this.sceneService.getSceneBounds(scene);
            previewMap.map.fitBounds(sceneBounds, {
                padding: [75, 75],
                animate: true
            });
        });
    }

    closeDetailPane() {
        if (this.activeScene) {
            delete this.activeScene;
            this.$parent.showPreviewMap = false;
            this.routeParams.sceneid = null;
            this.$state.go('.', this.getCombinedParams(), {notify: false});
            this.getPreviewMap().then((map) => {
                map.deleteThumbnail();
            });
            this.getMap().then((map) => {
                this.$timeout(() => {
                    map.map.invalidateSize();
                }, 200);
            });
        }
    }

    toggleSelectAndClosePane() {
        this.setSelected(this.activeScene, !this.isSelected(this.activeScene));
        this.closeDetailPane();
    }

    isInProject(scene) {
        if (this.projectScenesReady) {
            const index = this.projectSceneIds.indexOf(scene.id);
            return index >= 0;
        }
        return false;
    }

    gotoProjectScenes() {
        this.selectNoScenes();
        this.$state.go('projects.edit.scenes');
    }

    openSceneDetailModal(scene) {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        this.activeModal = this.$uibModal.open({
            component: 'rfSceneDetailModal',
            resolve: {
                scene: () => scene
            }
        });
    }
}
