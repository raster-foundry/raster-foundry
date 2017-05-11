export default class FilterPaneController {
    constructor(datasourceService, authService, $scope, $rootScope, $timeout,
                $uibModal, moment) {
        'ngInject';
        this.datasourceService = datasourceService;
        this.authService = authService;
        this.$scope = $scope;
        this.$rootScope = $rootScope;
        this.$timeout = $timeout;
        this.$uibModal = $uibModal;
        this.Moment = moment;
    }

    $onInit() {
        if (this.authService.isLoggedIn) {
            this.initFilters();
            this.initDatefilter();
        }

        this.toggleDrag = {toggle: false, enabled: false};

        this.$scope.$watch(() => this.authService.isLoggedIn, (isLoggedIn) => {
            if (isLoggedIn) {
                this.initFilters();
                this.initDatefilter();
            }
        });

        this.$scope.$watch('$ctrl.opened', (opened) => {
            if (opened) {
                this.$timeout(() => this.$rootScope.$broadcast('reCalcViewDimensions'), 50);
            }
        });
    }

    initDatefilter() {
        this.datefilter = {
            start: this.Moment().subtract(100, 'years'),
            end: this.Moment()
        };
        this.dateranges = [
            {
                name: 'Today',
                start: this.Moment(),
                end: this.Moment()
            },
            {
                name: 'The last month',
                start: this.Moment().subtract(1, 'months'),
                end: this.Moment()
            },
            {
                name: 'The last year',
                start: this.Moment().subtract(1, 'years'),
                end: this.Moment()
            },
            {
                name: 'All',
                start: this.Moment().subtract(100, 'years'),
                end: this.Moment()
            }
        ];

        if (this.filters.minAcquisitionDatetime && this.filters.maxAcquisitionDatetime) {
            this.datefilter.start = this.Moment(this.filters.minAcquisitionDatetime);
            this.datefilter.end = this.Moment(this.filters.maxAcquisitionDatetime);
        }

        if (!this.filters.minAcquisitionDatetime || !this.filters.maxAcquisitionDatetime) {
            this.clearDateFilter();
        } else {
            this.dateFilterToggle = {value: true};
        }
    }

    clearDateFilter() {
        delete this.filters.minAcquisitionDatetime;
        delete this.filters.maxAcquisitionDatetime;
        this.dateFilterToggle = {value: false};
    }

    setDateRange(start, end, preset) {
        this.datefilter.start = start;
        this.datefilter.end = end;
        this.datefilterPreset = preset || false;
        this.dateFilterToggle.value = true;
        this.filters.minAcquisitionDatetime = start.toISOString();
        this.filters.maxAcquisitionDatetime = end.toISOString();
        this.cacheYearFilters();
    }

    onDateFilterToggle(value) {
        if (value) {
            this.filters.minAcquisitionDatetime = this.datefilter.start.toISOString();
            this.filters.maxAcquisitionDatetime = this.datefilter.end.toISOString();
        } else {
            delete this.filters.minAcquisitionDatetime;
            delete this.filters.maxAcquisitionDatetime;
        }
    }

    close() {
        this.opened = false;
    }

    onCloudCoverFiltersChange(id, minModel, maxModel) {
        // Some scenes have a cloudCover < 0, which is invalid. filter them out.
        this.filters.minCloudCover = minModel;

        if (maxModel === this.cloudCoverRange.max) {
            delete this.filters.maxCloudCover;
        } else {
            this.filters.maxCloudCover = maxModel;
        }
    }

    onSunElevationFiltersChange(id, minModel, maxModel) {
        if (minModel === this.sunElevationRange.min) {
            delete this.filters.minSunElevation;
        } else {
            this.filters.minSunElevation = minModel;
        }

        if (maxModel === this.sunElevationRange.max) {
            delete this.filters.maxSunElevation;
        } else {
            this.filters.maxSunElevation = maxModel;
        }
    }

    onSunAzimuthFiltersChange(id, minModel, maxModel) {
        if (minModel === this.sunAzimuthRange.min) {
            delete this.filters.minSunAzimuth;
        } else {
            this.filters.minSunAzimuth = minModel;
        }

        if (maxModel === this.sunAzimuthRange.max) {
            delete this.filters.maxSunAzimuth;
        } else {
            this.filters.maxSunAzimuth = maxModel;
        }
    }

    initFilters() {
        this.cachedFilters = {};

        this.cloudCoverRange = {min: 0, max: 100};

        let minCloudCover = this.cloudCoverRange.min;
        if (this.filters.minCloudCover) {
            minCloudCover = parseInt(this.filters.minCloudCover, 10);
        }

        let maxCloudCover = parseInt(this.filters.maxCloudCover, 10) || 10;

        if (!this.filters.minCloudCover && minCloudCover !== this.cloudCoverRange.min) {
            this.filters.minCloudCover = minCloudCover;
        }

        if (!this.filters.maxCloudCover && maxCloudCover !== this.cloudCoverRange.max) {
            this.filters.maxCloudCover = maxCloudCover;
        }

        this.cloudCoverFilters = {
            minModel: minCloudCover,
            maxModel: maxCloudCover,
            options: {
                floor: this.cloudCoverRange.min,
                ceil: this.cloudCoverRange.max,
                minRange: 0,
                showTicks: 10,
                showTicksValues: true,
                step: 10,
                pushRange: true,
                draggableRange: true,
                onEnd: this.onCloudCoverFiltersChange.bind(this)
            }
        };

        this.sunElevationRange = {min: 0, max: 180};
        let minSunElevation = parseInt(this.filters.minSunElevation, 10) ||
            this.sunElevationRange.min;
        let maxSunElevation = parseInt(this.filters.maxSunElevation, 10) ||
            this.sunElevationRange.max;
        this.sunElevationFilters = {
            minModel: minSunElevation,
            maxModel: maxSunElevation,
            options: {
                floor: this.sunElevationRange.min,
                ceil: this.sunElevationRange.max,
                minRange: 0,
                showTicks: 30,
                showTicksValues: true,
                step: 10,
                pushRange: true,
                draggableRange: true,
                onEnd: this.onSunElevationFiltersChange.bind(this)
            }
        };

        this.sunAzimuthRange = {min: 0, max: 360};
        let minSunAzimuth = parseInt(this.filters.minSunAzimuth, 10) ||
            this.sunAzimuthRange.min;
        let maxSunAzimuth = parseInt(this.filters.maxSunAzimuth, 10) ||
            this.sunAzimuthRange.max;
        this.sunAzimuthFilters = {
            minModel: minSunAzimuth,
            maxModel: maxSunAzimuth,
            options: {
                floor: this.sunAzimuthRange.min,
                ceil: this.sunAzimuthRange.max,
                minRange: 0,
                showTicks: 60,
                showTicksValues: true,
                step: 10,
                pushRange: true,
                draggableRange: true,
                onEnd: this.onSunAzimuthFiltersChange.bind(this)
            }
        };

        this.initIngestFilter();

        this.initSourceFilters();
    }

    initIngestFilter() {
        this.ingestFilter = 'any';
        if (this.filters.hasOwnProperty('ingest')) {
            if (this.filters.ingest) {
                this.ingestFilter = 'ingested';
            } else {
                this.ingestFilter = 'uningested';
            }
        }
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
            if (this.filters.datasource) {
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
        this.clearDateFilter();

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

        this.ingestFilter = 'any';
        delete this.filters.ingested;

        delete this.filters.datasource;
    }

    setIngestFilter(mode) {
        this.ingestFilter = mode;
        this.onIngestFilterChange();
    }

    onIngestFilterChange() {
        if (this.ingestFilter === 'any') {
            delete this.filters.ingested;
        } else if (this.ingestFilter === 'uningested') {
            this.filters.ingested = false;
        } else {
            this.filters.ingested = true;
        }
    }


    cacheYearFilters() {
        this.cachedFilters.minAcquisitionDatetime = this.filters.minAcquisitionDatetime;
        this.cachedFilters.maxAcquisitionDatetime = this.filters.maxAcquisitionDatetime;
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

    openDateRangePickerModal() {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        this.activeModal = this.$uibModal.open({
            component: 'rfDateRangePickerModal',
            resolve: {
                config: () => Object({
                    range: this.datefilter,
                    ranges: this.dateranges
                })
            }
        });

        this.activeModal.result.then(
            range => {
                if (range) {
                    this.setDateRange(range.start, range.end, range.preset);
                }
            });
    }
}
