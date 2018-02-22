/* global BUILDCONFIG, document */
import _ from 'lodash';

class DatasourceDetailController {
    constructor(
        $stateParams, $log, $timeout,
        modalService, datasourceService, uuid4, authService, datasourceLicenseService
    ) {
        'ngInject';
        this.$log = $log;
        this.$timeout = $timeout;

        this.datasourceId = $stateParams.datasourceid;

        this.modalService = modalService;
        this.datasourceService = datasourceService;
        this.uuid4 = uuid4;
        this.authService = authService;
        this.datasourceLicenseService = datasourceLicenseService;
    }

    $onInit() {
        this.BUILDCONFIG = BUILDCONFIG;
        this.initLicenseSettings();
        this.loadDatasource();
    }

    initLicenseSettings() {
        this.selectedLicense = 'None';
        this.selectedLicenseUrl = '';
        this.emptyLicense = [{'short_name': 'None', 'name': 'None'}];
    }

    loadDatasource() {
        this.isLoadingDatasource = true;
        this.isLoadingDatasourceError = false;
        this.isDatasourceVisibilityUpdated = false;
        this.datasourceService.get(this.datasourceId).then(
            datasourceResponse => {
                this.datasource = datasourceResponse;
                this.isPublic = this.isPublicDatasource();
                let id = this.authService.getProfile().sub;
                this.isOwner = id === this.datasource.owner;
                this.initBuffers();
                // eslint-disable-next-line
                this.datasource.license_name = 'MIT-CMU';
                this.getLicense(this.datasource);
            },
            () => {
                this.isLoadingDatasourceError = true;
            }
        ).finally(() => {
            this.isLoadingDatasource = false;
        });
    }

    initBuffers() {
        this.colorCompositesBuffer = _.cloneDeep(this.datasource.composites);
        this.bandsBuffer = _.cloneDeep(this.datasource.bands);
    }

    getLicense(datasource) {
        if (datasource.license_name && datasource.license_name.length) {
            // TODO: (if needed) accomodate to the real license api to get this license's info
            this.datasourceLicenseService.get(datasource.license_name).then(
                response => {
                    this.license = JSON.parse(response);
                    if (!_.isEmpty(this.license)) {
                        // eslint-disable-next-line
                        this.selectedLicense = this.license.short_name;
                        this.selectedLicenseUrl = this.license.url;
                        this.selectedLicenseUrlDisplay = this.shortenUrl(this.selectedLicenseUrl);
                    }
                }
            );
        }
    }

    shortenUrl(url) {
        let segments = url.split('/');
        return `${segments[0]}//${segments[2]}/.../${url.substr(url.length - 15)}`;
    }

    onSelectLicense() {
        this.isSearchLicense = !this.isSearchLicense;
        this.$timeout(() => {
            const input = angular.element(document.querySelector('.license-input'));
            input.focus();
        }, 100);
        if (!this.licenses) {
            this.getAllLicenses();
        }
        this.showMatchedLicenses = true;
    }

    getAllLicenses() {
        // TODO: (if needed) accomodate to the real license api to get all licenses
        this.datasourceLicenseService.query().then(
            response => {
                this.licenses = JSON.parse(response);
                this.matchedLicenses = this.setMatchedLicensesDefault(this.licenses);
            }
        );
    }

    onLicenseInputChange() {
        if (this.licenseInput && this.licenseInput.length >= 3) {
            this.matchLicense(this.licenseInput);
        } else {
            this.showMatchedLicenses = true;
            this.matchedLicenses = this.setMatchedLicensesDefault(this.licenses);
        }
    }

    matchLicense(licenseInput) {
        if (this.licenses && this.licenses.length) {
            this.matchedLicenses = this.licenses.filter((license) => {
                return license.short_name.toUpperCase().includes(licenseInput.toUpperCase())
                || license.name.toUpperCase().includes(licenseInput.toUpperCase());
            });
            this.showMatchedLicenses = this.matchedLicenses.length;
        }
    }

    onLicenseFieldBlur() {
        if (!this.isMouseOnLicenseOption) {
            this.resetLicenseSearch();
        }
    }

    onLicenseClick(license) {
        this.selectedLicense = license.short_name;
        this.selectedLicenseUrlDisplay = license.url ? this.shortenUrl(license.url) : '';
        this.isMouseOnLicenseOption = false;
        this.resetLicenseSearch();
        // TODO: datasource does not have license name json interface built
        // will do the datasource update once ^ is done.
        this.$log.log(license);
    }

    resetLicenseSearch() {
        this.isSearchLicense = !this.isSearchLicense;
        this.licenseInput = '';
        this.matchedLicenses = this.setMatchedLicensesDefault(this.licenses);
        this.showMatchedLicenses = false;
    }

    onLicenseHover(isMouseHovered) {
        this.isMouseOnLicenseOption = isMouseHovered;
    }

    setMatchedLicensesDefault(licenses) {
        return this.emptyLicense.concat(licenses);
    }

    openImportModal() {
        this.modalService.open({
            component: 'rfSceneImportModal',
            resolve: {
                datasource: () => this.datasource
            }
        });
    }

    saveColorComposites() {
        let newBuffer = {};
        _.toPairs(this.colorCompositesBuffer).forEach(([, val]) => {
            delete val.changed;
            newBuffer[val.label] = val;
        });
        this.datasourceService.updateDatasource(Object.assign(this.datasource, {
            composites: newBuffer
        })).then((ds) => {
            this.datasource = ds;
            this.changedBuffer = false;
            this.colorCompositesBuffer = newBuffer;
        }, (err) => {
            this.$log.log('Error saving datasource', err);
        });
    }

    cancel() {
        this.initBuffers();
    }

    notDefaultDatasource() {
        if (this.datasource) {
            return this.datasource.owner !== 'default';
        }
        return false;
    }

    isPublicDatasource() {
        if (this.datasource) {
            return this.datasource.visibility === 'PUBLIC';
        }
        return false;
    }

    changeVisibility() {
        this.datasource.visibility = this.datasource.visibility === 'PUBLIC' ? 'PRIVATE' : 'PUBLIC';
        this.isPublic = !this.isPublic;
        this.datasourceService.updateDatasource(this.datasource).then(
            () => {
                this.isDatasourceVisibilityUpdated = true;
            },
            () => {
                this.isDatasourceVisibilityUpdated = false;
            }
        );
    }

    addBand() {
        this.bandsBuffer = [
            ...this.bandsBuffer,
            {
                name: '',
                number: ''
            }
        ];
        this.changedBandsBuffer = true;
    }

    removeBand(index) {
        this.bandsBuffer = [
            ...this.bandsBuffer.slice(0, index),
            ...this.bandsBuffer.slice(index + 1, this.bandsBuffer.length)
        ];
        this.changedBandsBuffer = true;
    }

    updateBandBuffer(index, band) {
        this.bandsBuffer[index] = band;
        this.changedBandsBuffer = true;
    }

    saveBufferedBands() {
        this.datasourceService.updateDatasource(Object.assign(this.datasource, {
            bands: this.bandsBuffer
        })).then((ds) => {
            this.datasource = ds;
            this.resetBandsBuffer();
        }, (err) => {
            this.$log.log('Error saving datasource', err);
        });
    }

    resetBandsBuffer() {
        this.bandsBuffer = _.cloneDeep(this.datasource.bands);
        this.changedBandsBuffer = false;
    }

    addCompositeRow() {
        this.colorCompositesBuffer[this.uuid4.generate()] = {
            value: {redBand: 0, greenBand: 1, blueBand: 2}
        };
    }

    updateBuffer(preset, key, val) {
        this.onBufferChange(preset);
        this.colorCompositesBuffer[preset].value[key] = +val;
    }

    onBufferChange(preset) {
        if (preset) {
            this.colorCompositesBuffer[preset].changed = true;
        }
        this.changedBuffer = true;
    }

    deleteFromBuffer(preset) {
        delete this.colorCompositesBuffer[preset];
        this.onBufferChange();
    }

    cancel() {
        this.changedBuffer = false;
        this.colorCompositesBuffer = _.cloneDeep(this.datasource.composites);
        this.$log.error(this.colorCompositesBuffer);
    }
}

const DatasourceDetailModule = angular.module('pages.imports.datasources.detail', []);

DatasourceDetailModule.controller('DatasourceDetailController', DatasourceDetailController);

export default DatasourceDetailModule;
