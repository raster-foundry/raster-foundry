/* global BUILDCONFIG */
import _ from 'lodash';

class DatasourceDetailController {
    constructor(
        $stateParams, modalService, datasourceService, uuid4, $log, authService
    ) {
        'ngInject';
        this.modalService = modalService;
        this.datasourceId = $stateParams.datasourceid;
        this.datasourceService = datasourceService;
        this.uuid4 = uuid4;
        this.$log = $log;
        this.authService = authService;
    }

    $onInit() {
        this.BUILDCONFIG = BUILDCONFIG;
        this.loadDatasource();
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
        })).then(() => {
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
