/* globals _*/
const cloudCoverRange = {min: 0, max: 100};
const sunElevationRange = {min: 0, max: 180};
const sunAzimuthRange = {min: 0, max: 360};
const planetItemTypes = [
  {itemType: 'PSScene3Band', name: 'PlanetScope Scenes - 3 band'},
  {itemType: 'PSScene4Band', name: 'PlanetScope Scenes - 4 band'},
  {itemType: 'PSOrthoTile', name: 'PlanetScope OrthoTiles'},
  {itemType: 'REOrthoTile', name: 'RapidEye OrthoTiles'}
];

export default class FilterPaneController {
    constructor($log, $scope, $rootScope, $timeout, modalService,
        datasourceService, authService, userService, moment) {
        'ngInject';
        this.$log = $log;
        this.$scope = $scope;
        this.$rootScope = $rootScope;
        this.$timeout = $timeout;
        this.modalService = modalService;
        this.datasourceService = datasourceService;
        this.authService = authService;
        this.userService = userService;
        this.Moment = moment;
    }

    $onInit() {
        this.newParams = Object.assign({}, this.filters);
        this.authService.getCurrentUser().then((user) => {
            this.userPlanetCredential = user.planetCredential;
        });
        this.toggleDrag = {toggle: false, enabled: false};
        this.initDataRepoFilter();
        this.initDataSourceFilters();
        this.initDatefilter();
        this.initFilterSlideOptions(false);
        this.initIngestFilter();
        this.importOwnerFilter = this.filters.owner ? 'user' : 'any';
    }

    $onChanges(changes) {
        if (changes.opened && changes.opened.hasOwnProperty('currentValue')) {
            if (changes.opened.currentValue) {
                this.$timeout(() => this.$rootScope.$broadcast('reCalcViewDimensions'), 50);
            }
        }
    }

    onClose() {
        this.onCloseFilterPane({showFilterPane: false});
    }

    onSelectBrowseSource(browseSource) {
        if (browseSource !== this.selectedBrowseSource) {
            if (browseSource === 'Planet Labs') {
                if (!this.userPlanetCredential) {
                    this.connectToPlanet();
                }
                this.onPassPlanetToken({planetToken: this.userPlanetCredential});
            }
            this.selectedBrowseSource = browseSource;
            this.selectedDatasource = '';
            this.initDataSourceFilters();
            this.newParams.datasource = [];
            this.onFilterChange({
                newFilters: this.newParams,
                sourceRepo: this.selectedBrowseSource
            });
        }
    }

    connectToPlanet() {
        this.activeModal = this.modalSevice.open({
            component: 'rfEnterTokenModal',
            resolve: {
                title: () => 'Enter your Planet API Token'
            }
        });
        this.activeModal.result.then((token) => {
            this.userService.updatePlanetToken(token).then(() => {
                this.userPlanetCredential = token;
                if (this.userPlanetCredential) {
                    this.selectedBrowseSource = 'Planet Labs';
                }
            }, (err) => {
                this.$log.log('There was an error updating the user with a planet api token', err);
            });
        });
    }

    initDataRepoFilter() {
        this.selectedBrowseSource = this.filters && this.filters.dataRepo ?
          this.filters.dataRepo : 'Raster Foundry';
    }

    initDataSourceFilters() {
        if (this.selectedBrowseSource === 'Raster Foundry') {
            this.datasourceService.query().then(d => {
                this.datasources = d.results;
                if (this.filters && this.filters.datasource && this.filters.datasource[0]) {
                    let matchedSource = this.datasources.find((ds) => {
                        return ds.id === this.filters.datasource[0];
                    });
                    if (matchedSource) {
                        this.selectedDatasource = matchedSource.name;
                    } else {
                        this.selectedDatasource = '';
                    }
                }
            });
        } else if (this.selectedBrowseSource === 'Planet Labs') {
            this.datasources = planetItemTypes;
            if (this.filters && this.filters.datasource && this.filters.datasource[0]) {
                let matchedSource = this.datasources.find((ds) => {
                    return ds.itemType === this.filters.datasource[0];
                });
                if (matchedSource) {
                    this.selectedDatasource = matchedSource.name;
                } else {
                    this.selectedDatasource = '';
                }
            }
        }
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
                name: 'None',
                start: {},
                end: {}
            }
        ];

        if (this.filters.minAcquisitionDatetime && this.filters.maxAcquisitionDatetime) {
            this.datefilter.start = this.Moment(this.filters.minAcquisitionDatetime);
            this.datefilter.end = this.Moment(this.filters.maxAcquisitionDatetime);
            this.hasDatetimeFilter = true;
        }

        if (!this.filters.minAcquisitionDatetime || !this.filters.maxAcquisitionDatetime) {
            this.clearDateFilter(false);
        } else {
            this.datefilterPreset = '';
            this.hasDatetimeFilter = true;
        }
    }

    initFilterSlideOptions(isReset) {
        if (this.filters) {
            this.filterOptions = {
                cloudCover: {
                    minModel: isReset ? cloudCoverRange.min :
                      this.filters.minCloudCover || cloudCoverRange.min,
                    maxModel: isReset ? cloudCoverRange.max :
                      this.filters.maxCloudCover || cloudCoverRange.max,
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
                            this.onFilterUpdate({
                                minCloudCover: minModel !== cloudCoverRange.min ? minModel : null,
                                maxCloudCover: maxModel !== cloudCoverRange.max ? maxModel : null
                            });
                        }
                    }
                },
                sunElevation: {
                    minModel: isReset ? sunElevationRange.min :
                      this.filters.minSunElevation || sunElevationRange.min,
                    maxModel: isReset ? sunElevationRange.max :
                      this.filters.maxSunElevation || sunElevationRange.max,
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
                            this.onFilterUpdate({
                                minSunElevation:
                                  minModel !== sunElevationRange.min ? minModel : null,
                                maxSunElevation:
                                  maxModel !== sunElevationRange.max ? maxModel : null
                            });
                        }
                    }
                },
                sunAzimuth: {
                    minModel: isReset ? sunAzimuthRange.min :
                      this.filters.minSunAzimuth || sunAzimuthRange.min,
                    maxModel: isReset ? sunAzimuthRange.max :
                      this.filters.maxSunAzimuth || sunAzimuthRange.max,
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
                            this.onFilterUpdate({
                                minSunAzimuth: minModel !== sunAzimuthRange.min ? minModel : null,
                                maxSunAzimuth: maxModel !== sunAzimuthRange.max ? maxModel : null
                            });
                        }
                    }
                }
            };
        }
    }

    initIngestFilter() {
        if (this.filters.hasOwnProperty('ingested')) {
            if (this.filters.ingested) {
                this.ingestFilter = 'ingested';
            } else if (this.filters.ingested === null) {
                this.ingestFilter = 'any';
            } else {
                this.ingestFilter = 'uningested';
            }
        }
    }

    toggleSourceFilter(source) {
        if (this.selectedBrowseSource === 'Raster Foundry') {
            this.onFilterUpdate({datasource: [source.id]});
        } else if (this.selectedBrowseSource === 'Planet Labs') {
            this.onFilterUpdate({datasource: [source.itemType]});
        }
    }

    clearDatasourceFilter() {
        this.selectedDatasource = '';
        this.onFilterUpdate({datasource: []});
    }

    openDateRangePickerModal() {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        this.activeModal = this.modalSevice.open({
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

    setDateRange(start, end, preset) {
        if (_.isEmpty({start}) || _.isEmpty(end)) {
            this.clearDateFilter(false);
        } else {
            this.datefilter.start = start;
            this.datefilter.end = end;
            this.datefilterPreset = preset || false;
            this.hasDatetimeFilter = true;
            this.onFilterUpdate({
                minAcquisitionDatetime: start.toISOString(),
                maxAcquisitionDatetime: end.toISOString()
            });
        }
    }

    clearDateFilter(isResetAll) {
        this.datefilterPreset = 'None';
        this.hasDatetimeFilter = false;
        if (!isResetAll) {
            this.onFilterUpdate({
                minAcquisitionDatetime: null,
                maxAcquisitionDatetime: null
            });
        }
    }

    setIngestFilter(mode) {
        this.ingestFilter = mode;
        this.onIngestFilterChange();
    }

    onIngestFilterChange() {
        if (this.ingestFilter === 'any') {
            this.onFilterUpdate({ingested: null});
        } else if (this.ingestFilter === 'uningested') {
            this.onFilterUpdate({ingested: false});
        } else {
            this.onFilterUpdate({ingested: true});
        }
    }

    setImportOwnerFilter(mode) {
        this.importOwnerFilter = mode;
        this.onImportOwnerFilterChange();
    }

    onImportOwnerFilterChange() {
        if (this.importOwnerFilter === 'user') {
            let profile = this.authService.profile();
            this.onFilterUpdate({owner: profile ? profile.user_id : null});
        } else {
            this.onFilterUpdate({owner: null});
        }
    }

    // let profile = this.authService.getProfile();
    // this.filters.owner = profile ? profile.sub : null;

    resetAllFilters() {
        this.selectedDatasource = '';
        this.clearDateFilter(true);
        this.initFilterSlideOptions(true);
        let emptyFilter = {
            datasource: [],
            minCloudCover: null,
            maxCloudCover: null,
            minSunElevation: null,
            maxSunElevation: null,
            minSunAzimuth: null,
            maxSunAzimuth: null,
            minAcquisitionDatetime: null,
            maxAcquisitionDatetime: null
        };
        if (this.selectedBrowseSource === 'Raster Foundry') {
            this.ingestFilter = 'any';
            this.importOwnerFilter = 'any';
            this.onFilterUpdate(Object.assign({}, emptyFilter, {ingested: null, owner: null}));
        } else if (this.selectedBrowseSource === 'Planet Labs') {
            this.onFilterUpdate(emptyFilter);
        }
    }

    onFilterUpdate(changesObj) {
        this.newParams = Object.assign(this.newParams, changesObj);
        this.onFilterChange({
            newFilters: this.newParams,
            sourceRepo: this.selectedBrowseSource
        });
    }
}
