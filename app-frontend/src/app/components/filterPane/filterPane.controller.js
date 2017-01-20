import _ from 'lodash';
export default class FilterPaneController {
    constructor() {
        'ngInject';

        this.toggleDrag = {toggle: false, enabled: false};
        this.initFilters();
    }

    close() {
        this.onCloseClick();
    }

    onYearFiltersChange(id, minModel, maxModel) {
        if (minModel === this.yearRange.min) {
            delete this.filters.minAcquisitionDatetime;
        } else {
            this.filters.minAcquisitionDatetime =
                (new Date(minModel, 0, 1))
                .toISOString();
        }

        if (maxModel === this.yearRange.max) {
            delete this.filters.maxAcquisitionDatetime;
        } else {
            this.filters.maxAcquisitionDatetime =
                (new Date(maxModel, 11, 31))
                .toISOString();
        }
    }

    onCloudCoverFiltersChange(id, minModel, maxModel) {
        if (minModel === this.cloudCoverRange.min) {
            delete this.filters.minCloudCover;
        } else {
            this.filters.minCloudCover = minModel;
        }

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
        this.yearRange = {min: 2010, max: 2016};
        let minAcquisitionDatetime =
            (new Date(this.filters.minAcquisitionDatetime || NaN)).getFullYear() ||
            this.yearRange.min;
        let maxAcquisitionDatetime =
            (new Date(this.filters.maxAcquisitionDatetime || NaN)).getFullYear() ||
            this.yearRange.max;
        this.yearFilters = {
            minModel: minAcquisitionDatetime,
            maxModel: maxAcquisitionDatetime,
            options: {
                floor: this.yearRange.min,
                ceil: this.yearRange.max,
                minRange: 1,
                showTicks: 1,
                showTicksValues: true,
                pushRange: true,
                draggableRange: true,
                onEnd: this.onYearFiltersChange.bind(this)
            }
        };

        this.initMonthFilters();

        this.cloudCoverRange = {min: 0, max: 100};
        let minCloudCover = parseInt(this.filters.minCloudCover, 10) ||
            this.cloudCoverRange.min;
        let maxCloudCover = parseInt(this.filters.maxCloudCover, 10) ||
            this.cloudCoverRange.max;
        this.cloudCoverFilters = {
            minModel: minCloudCover,
            maxModel: maxCloudCover,
            options: {
                floor: this.cloudCoverRange.min,
                ceil: this.cloudCoverRange.max,
                minRange: 10,
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
                minRange: 10,
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
                minRange: 10,
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

    initMonthFilters() {
        this.monthFilters = {
            1: {label: 'Jan', enabled: false},
            2: {label: 'Feb', enabled: false},
            3: {label: 'Mar', enabled: false},
            4: {label: 'Apr', enabled: false},
            5: {label: 'May', enabled: false},
            6: {label: 'Jun', enabled: false},
            7: {label: 'Jul', enabled: false},
            8: {label: 'Aug', enabled: false},
            9: {label: 'Sep', enabled: false},
            10: {label: 'Oct', enabled: false},
            11: {label: 'Nov', enabled: false},
            12: {label: 'Dec', enabled: false}
        };

        if (this.filters.month) {
            if (!isNaN(this.filters.month)) {
                this.monthFilters[parseInt(this.filters.month, 10)].enabled = true;
            } else if (
                Object.prototype.toString.call(this.filters.month)
                    === '[object Array]'
            ) {
                this.monthFilters = _.mapValues(
                    this.monthFilters,
                    (val, key) => {
                        val.enabled = this.filters.month.indexOf(key) !== -1;
                        return val;
                    }
                );
            } else {
                this.filters.month = null;
            }
        }
    }

    initSourceFilters() {
        this.sourceFilters = {
            self: {label: 'My Imports', enabled: false},
            users: {label: 'Raster Foundry Users', enabled: false},
            'Sentinel-2': {label: 'Sentinel-2', enabled: false},
            'Landsat 8': {label: 'Landsat 8', enabled: false}
        };
        if (this.filters.datasource) {
            if (
                Object.prototype.toString.call(this.filters.datasource)
                    === '[object Array]'
            ) {
                this.sourceFilters = _.mapValues(
                    this.sourceFilters,
                    (val, key) => {
                        val.enabled = this.filters.datasource.indexOf(key) !== -1;
                        return val;
                    }
                );
            } else {
                this.sourceFilters[this.filters.datasource].enabled = true;
            }
        }
    }

    resetAllFilters() {
        this.yearFilters.minModel = this.yearRange.min;
        this.yearFilters.maxModel = this.yearRange.max;
        delete this.filters.minAcquisitionDatetime;
        delete this.filters.maxAcquisitionDatetime;

        this.monthFilters = _.mapValues(this.monthFilters, (val) => {
            val.enabled = false;
            return val;
        });
        delete this.filters.month;

        this.cloudCoverFilters.minModel = this.cloudCoverRange.min;
        this.cloudCoverFilters.maxModel = this.cloudCoverRange.max;
        delete this.filters.minCloudCover;
        delete this.filters.maxCloudCover;


        this.sunElevationFilters.minModel = this.sunElevationRange.min;
        this.sunElevationFilters.maxModel = this.sunElevationRange.max;
        delete this.filters.minSunElevation;
        delete this.filters.maxSunElevation;

        this.sunAzimuthFilters.minModel = this.sunAzimuthRange.min;
        this.sunAzimuthFilters.maxModel = this.sunAzimuthRange.max;
        delete this.filters.minSunAzimuth;
        delete this.filters.maxSunAzimuth;

        this.sourceFilters = _.mapValues(this.sourceFilters, (val) => {
            val.enabled = false;
            return val;
        });

        this.ingestFilter = 'any';
        delete this.filters.ingested;

        delete this.filters.datasource;
    }

    onMonthFilterChange(newVal) {
        if (!newVal || !this.filters) {
            return;
        }

        let enabledMonths = Object.keys(newVal).filter((key) => {
            return newVal[key].enabled;
        }).map((key) => {
            let month = {};
            month[key] = newVal[key];
            return month;
        });

        if (enabledMonths.length === 0) {
            delete this.filters.month;
        } else {
            this.filters.month = [];
            enabledMonths.forEach((monthAttr) => {
                this.filters.month.push(Object.keys(monthAttr)[0]);
            });
        }
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

    setMonthFilter(month, enabled) {
        this.monthFilters[month].enabled = enabled;
        this.onMonthFilterChange(this.monthFilters);
    }

    onMonthFilterMousedown(filter, filterAttrs) {
        this.toggleDrag.toggle = true;
        this.toggleDrag.enabled = !filterAttrs.enabled;
        this.setMonthFilter(filter, this.toggleDrag.enabled);
    }

    onMonthFilterMouseover(filter) {
        if (this.toggleDrag.toggle) {
            this.setMonthFilter(filter, this.toggleDrag.enabled);
        }
    }

    onSourceFilterChange(newVal) {
        if (!newVal || !this.filters) {
            return;
        }

        let enabledSources = Object.keys(newVal).filter((key) => {
            return newVal[key].enabled;
        }).map((key) => {
            let source = {};
            source[key] = newVal[key];
            return source;
        });

        if (enabledSources.length === 0) {
            delete this.filters.datasource;
        } else {
            this.filters.datasource = [];
            enabledSources.forEach((sourceAttrs) => {
                this.filters.datasource.push(Object.keys(sourceAttrs)[0]);
            });
        }
    }

    setSourceFilter(source, enabled) {
        this.sourceFilters[source].enabled = enabled;
        this.onSourceFilterChange(this.sourceFilters);
    }

    onSourceFilterMousedown(filter, filterAttrs) {
        this.toggleDrag.toggle = true;
        this.toggleDrag.enabled = !filterAttrs.enabled;
        this.setSourceFilter(filter, this.toggleDrag.enabled);
    }

    onSourceFilterMouseover(filter) {
        if (this.toggleDrag.toggle) {
            this.setSourceFilter(filter, this.toggleDrag.enabled);
        }
    }
}
