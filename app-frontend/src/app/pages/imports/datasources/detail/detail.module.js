/* global BUILDCONFIG, document */
import _ from 'lodash';

class DatasourceDetailController {
    constructor(
        $log, $timeout, $q, $stateParams,
        modalService, datasourceService, uuid4, authService, datasourceLicenseService
    ) {
        'ngInject';
        this.$log = $log;
        this.$timeout = $timeout;
        this.$q = $q;

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
        this.bandsBuffer = [];
    }

    initLicenseSettings() {
        this.emptyLicense = [{'shortName': null, 'name': null, 'url': ''}];
        this.selectedLicense = Object.assign({}, this.emptyLicense[0]);

        this.datasourceLicenseService.getLicenses().then((res) => {
            this.getAllLicenses(Math.ceil(res.count / res.pageSize) + 1);
        }, (err) => {
            this.$log.error(err);
        });
    }

    loadDatasource() {
        this.isLoadingDatasource = true;
        this.isLoadingDatasourceError = false;
        this.isDatasourceVisibilityUpdated = false;
        this.datasourceService.get(this.datasourceId).then(
            datasourceResponse => {
                this.datasource = datasourceResponse;
                this.hasBands = !_.isEmpty(this.datasource.bands);
                this.isPublic = this.isPublicDatasource();
                let id = this.authService.getProfile().sub;
                this.isOwner = id === this.datasource.owner;
                this.initBuffers();
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
        if (datasource.licenseName && datasource.licenseName.length) {
            this.datasourceLicenseService.getLicense(datasource.licenseName).then(
                (res) => {
                    if (!_.isEmpty(res)) {
                        this.selectedLicense = Object.assign({}, res);
                    }
                },
                (err) => {
                    this.$log.error(err);
                }
            );
        }
    }

    onSelectLicenseStart() {
        this.isSearchLicense = !this.isSearchLicense;
        this.$timeout(() => {
            const input = angular.element(document.querySelector('.license-input'));
            input.focus();
        }, 100);
        this.showMatchedLicenses = true;
    }

    getAllLicenses(pages) {
        let promises = _.times(pages, (idx) => {
            return this.datasourceLicenseService.getLicenses({page: idx})
              .then(resp => resp, error => error);
        });
        this.$q.all(promises).then((response) => {
            this.licenses = _.flatMap(response, r => r.results);
            this.matchedLicenses = this.setMatchedLicensesDefault(this.licenses);
        }, (err) =>{
            this.$log.error(err);
        });
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
                return license.shortName.toUpperCase().includes(licenseInput.toUpperCase())
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
        this.selectedLicense = Object.assign({}, license);
        this.isMouseOnLicenseOption = false;
        this.resetLicenseSearch();
        this.datasourceService.updateDatasource(Object.assign(this.datasource, {
            licenseName: license.shortName
        })).then((ds) => {
            this.datasource = ds;
        }, (err) => {
            this.$log.error('Error saving datasource', err);
        });
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
                datasource: () => this.datasource,
                origin: () => 'datasource'
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
            this.$log.error('Error saving datasource', err);
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
                number: '',
                wavelength: null,
                invalidWavelength: true
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
        if (band.wavelength === null || this.testWavelength(band.wavelength)) {
            band.invalidWavelength = false;
        } else {
            band.invalidWavelength = true;
        }
        this.bandsBuffer[index] = band;
        this.changedBandsBuffer = true;
    }

    testWavelength(wavelengthStr) {
        let wavelengthRegex = /^ *\d+(,| )+( *)\d+ *$/;
        return wavelengthRegex.test(wavelengthStr);
    }

    anyInvalidWavelengths() {
        return this.bandsBuffer.length === 0 ?
            false :
            _.reduce(
                this.bandsBuffer, (prior, band) => {
                    return band.invalidWavelength || prior;
                }, false
            );
    }

    saveBufferedBands() {
        this.datasourceService.updateDatasource(Object.assign(this.datasource, {
            bands: this.bandsBuffer
        })).then((ds) => {
            this.datasource = ds;
            this.resetBandsBuffer();
        }, (err) => {
            this.$log.error('Error saving datasource', err);
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
