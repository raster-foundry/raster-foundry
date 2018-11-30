const cloudCoverRange = {min: 0, max: 100};
const sunElevationRange = {min: 0, max: 180};
const sunAzimuthRange = {min: 0, max: 360};

export default class AOIFilterPaneController {
    constructor(
        $scope, $rootScope, $timeout, modalService,
        datasourceService, authService, moment, RasterFoundryRepository
    ) {
        'ngInject';
        this.$scope = $scope;
        this.$rootScope = $rootScope;
        this.$timeout = $timeout;
        this.modalService = modalService;
        this.datasourceService = datasourceService;
        this.authService = authService;
        this.Moment = moment;
        this.RasterFoundryRepository = RasterFoundryRepository;
    }

    $onInit() {
        this.toggleDrag = {toggle: false, enabled: false};
        this.initDatasourceFilters();
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

    initDatasourceFilters() {
        this.datasourceFilter = this.RasterFoundryRepository.getFilters()[0];
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
            this.filterOptions.sunElevation.maxModel = sceneParams.maxSunElevation;
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
        this.onCloseFilterPane({showFilterPane: false});
    }

    resetAllFilters() {
        this.filterOptions.cloudCover.minModel = cloudCoverRange.min;
        this.filterOptions.cloudCover.maxModel = cloudCoverRange.max;

        this.filterOptions.sunElevation.minModel = sunElevationRange.min;
        this.filterOptions.sunElevation.maxModel = sunElevationRange.max;

        this.filterOptions.sunAzimuth.minModel = sunAzimuthRange.min;
        this.filterOptions.sunAzimuth.maxModel = sunAzimuthRange.max;

        this.initDatasourceFilters();
        this.onFilterChange({changes: {
            datasource: [],
            minCloudCover: null,
            minSunElevation: null,
            maxSunElevation: null,
            minSunAzimuth: null,
            maxSunAzimuth: null
        }});
    }

    onDatasourceFilterChange(filter, filterParams) {
        this.onFilterChange({changes: {datasource: [filterParams.datasource]}});
    }
}
