const cloudCoverRange = {min: 0, max: 100};
const sunElevationRange = {min: 0, max: 180};
const sunAzimuthRange = {min: 0, max: 360};

export default class AOIFilterPaneController {
    constructor(
        $scope, $rootScope, $timeout, $uibModal,
        datasourceService, authService, moment
    ) {
        'ngInject';
        this.$scope = $scope;
        this.$rootScope = $rootScope;
        this.$timeout = $timeout;
        this.$uibModal = $uibModal;
        this.datasourceService = datasourceService;
        this.authService = authService;
        this.Moment = moment;
    }

    $onInit() {
        this.toggleDrag = {toggle: false, enabled: false};
    }

    $onChanges(changes) {
        if (changes.opened && changes.opened.hasOwnProperty('currentValue')) {
            if (changes.opened.currentValue) {
                this.$timeout(() => this.$rootScope.$broadcast('reCalcViewDimensions'), 150);
            }
        }

        if (changes.filters && changes.filters.currentValue) {
            if (!this.filterOptions) {
                this.initFilterOptions();
            }
            this.setFilters(changes.filters.currentValue);
        }
    }

    initFilterOptions() {
        this.filterOptions = {
            cloudCover: {
                minModel: cloudCoverRange.min,
                maxModel: cloudCoverRange.max,
                options: {
                    floor: cloudCoverRange.min,
                    ceil: cloudCoverRange.max,
                    minRange: 0,
                    showTicks: 10,
                    showTicksValues: true,
                    step: 10,
                    pushRange: true,
                    draggableRange: true,
                    onEnd: (id, minModel, maxModel) => {
                        this.onFilterChange({changes: {
                            minCloudCover: minModel !== cloudCoverRange.min ? minModel : null,
                            maxCloudCover: maxModel !== cloudCoverRange.max ? maxModel : null
                        }});
                    }
                }
            },
            sunElevation: {
                minModel: sunElevationRange.min,
                maxModel: sunElevationRange.max,
                options: {
                    floor: sunElevationRange.min,
                    ceil: sunElevationRange.max,
                    minRange: 0,
                    showTicks: 30,
                    showTicksValues: true,
                    step: 10,
                    pushRange: true,
                    draggableRange: true,
                    onEnd: (id, minModel, maxModel) => {
                        this.onFilterChange({changes: {
                            minSunElevation: minModel !== sunElevationRange.min ? minModel : null,
                            maxSunElevation: maxModel !== sunElevationRange.max ? maxModel : null
                        }});
                    }
                }
            },
            sunAzimuth: {
                minModel: sunAzimuthRange.min,
                maxModel: sunAzimuthRange.max,
                options: {
                    floor: sunAzimuthRange.min,
                    ceil: sunAzimuthRange.max,
                    minRange: 0,
                    showTicks: 60,
                    showTicksValues: true,
                    step: 10,
                    pushRange: true,
                    draggableRange: true,
                    onEnd: (id, minModel, maxModel) => {
                        this.onFilterChange({changes: {
                            minSunAzimuth: minModel !== sunAzimuthRange.min ? minModel : null,
                            maxSunAzimuth: maxModel !== sunAzimuthRange.max ? maxModel : null
                        }});
                    }
                }
            }
        };
    }

    setFilters(filters) {
        let sceneParams = filters.sceneParams;

        if (sceneParams && sceneParams.hasOwnProperty('maxCloudCover')) {
            this.filterOptions.cloudCover.maxModel = sceneParams.maxCloudCover;
        } else {
            this.filterOptions.cloudCover.maxModel = cloudCoverRange.max;
        }

        if (sceneParams && sceneParams.hasOwnProperty('minCloudCover')) {
            this.filterOptions.cloudCover.minModel = sceneParams.minCloudCover;
        } else {
            this.filterOptions.cloudCover.minModel = cloudCoverRange.min;
        }

        if (sceneParams && sceneParams.hasOwnProperty('maxSunElevation')) {
            this.filterOptions.sunElevation.maxModel = sceneParams.minSunElevation;
        } else {
            this.filterOptions.sunElevation.maxModel = sunElevationRange.max;
        }

        if (sceneParams && sceneParams.hasOwnProperty('minSunElevation')) {
            this.filterOptions.sunElevation.minModel = sceneParams.minSunElevation;
        } else {
            this.filterOptions.sunElevation.minModel = sunElevationRange.min;
        }

        if (sceneParams && sceneParams.hasOwnProperty('maxSunAzimuth')) {
            this.filterOptions.sunAzimuth.maxModel = sceneParams.maxSunAzimuth;
        } else {
            this.filterOptions.sunAzimuth.maxModel = sunAzimuthRange.max;
        }

        if (sceneParams && sceneParams.hasOwnProperty('minSunAzimuth')) {
            this.filterOptions.sunAzimuth.minModel = sceneParams.minSunAzimuth;
        } else {
            this.filterOptions.sunAzimuth.minModel = sunAzimuthRange.min;
        }
    }

    close() {
        this.opened = false;
    }

    initSourceFilters() {
        this.datasourceService.query().then(d => {
            this.datasources = d.results;
            this.dynamicSourceFilters = {};
            d.results.forEach(ds => {
                this.dynamicSourceFilters[ds.id] = {
                    datasource: ds,
                    enabled: false
                };
            });
            if (this.filterOptions.datasource) {
                if (Array.isArray(this.filters.datasource)) {
                    this.filters.datasource.forEach(dsf => {
                        if (this.dynamicSourceFilters[dsf]) {
                            this.dynamicSourceFilters[dsf].enabled = true;
                        }
                    });
                } else if (this.dynamicSourceFilters[this.filters.datasource]) {
                    this.dynamicSourceFilters[this.filters.datasource].enabled = true;
                }
            }
        });

        // Define static source filters
        this.staticSourceFilters = {
            mine: {
                datasource: {
                    id: 'mine',
                    name: 'My Imports'
                },
                enabled: false
            },
            users: {
                datasource: {
                    id: 'users',
                    name: 'Raster Foundry Users'
                },
                enabled: false
            }
        };

        if (this.filters.datasource) {
            if (Array.isArray(this.filters.datasource)) {
                this.filters.datasource.forEach(dsf => {
                    if (this.staticSourceFilters[dsf]) {
                        this.staticSourceFilters[dsf].enabled = true;
                    }
                });
            } else if (this.staticSourceFilters[this.filters.datasource]) {
                this.staticSourceFilters[this.filters.datasource].enabled = true;
            }
        }
    }

    resetAllFilters() {
        this.cloudCoverFilters.minModel = this.cloudCoverRange.min;
        this.cloudCoverFilters.maxModel = this.cloudCoverRange.max;
        this.filters.minCloudCover = this.cloudCoverRange.min;
        delete this.filters.maxCloudCover;


        this.sunElevationFilters.minModel = this.sunElevationRange.min;
        this.sunElevationFilters.maxModel = this.sunElevationRange.max;
        delete this.filters.minSunElevation;
        delete this.filters.maxSunElevation;

        this.sunAzimuthFilters.minModel = this.sunAzimuthRange.min;
        this.sunAzimuthFilters.maxModel = this.sunAzimuthRange.max;
        delete this.filters.minSunAzimuth;
        delete this.filters.maxSunAzimuth;

        Object.values(this.dynamicSourceFilters)
            .forEach((ds) => {
                ds.enabled = false;
            });
        Object.values(this.staticSourceFilters)
            .forEach((ds) => {
                ds.enabled = false;
            });
        this.filters.datasource = [];

        delete this.filters.datasource;
    }

    onSourceFilterChange() {
        this.filters.datasource = [];

        Object.values(this.dynamicSourceFilters)
            .filter(ds => ds.enabled)
            .forEach(ds => this.filters.datasource.push(ds.datasource.id));

        Object.values(this.staticSourceFilters)
            .filter(ds => ds.enabled)
            .forEach(ds => this.filters.datasource.push(ds.datasource.id));
    }

    toggleSourceFilter(sourceId) {
        if (this.dynamicSourceFilters[sourceId]) {
            this.dynamicSourceFilters[sourceId].enabled =
                !this.dynamicSourceFilters[sourceId].enabled;
        } else if (this.staticSourceFilters[sourceId]) {
            this.staticSourceFilters[sourceId].enabled =
                !this.staticSourceFilters[sourceId].enabled;
        }
        this.onSourceFilterChange();
    }
}
