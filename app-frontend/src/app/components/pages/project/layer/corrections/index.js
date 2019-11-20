import { Set } from 'immutable';
import _ from 'lodash';
import tpl from './index.html';

const mapLayerName = 'project-color-correction';

const baseGammaOptions = {
    floor: 0,
    ceil: 2,
    step: 0.01,
    showTicks: false,
    precision: 2,
    autoHideLimitLabels: false,
    showSelectionBar: true
};

const baseSaturationOptions = {
    floor: 0,
    ceil: 2,
    step: 0.01,
    showTicks: false,
    precision: 2,
    autoHideLimitLabels: false,
    showSelectionBar: true
};

const alphaOptions = {
    value: 1,
    floor: 0,
    ceil: 1,
    step: 0.01,
    showTicks: false,
    precision: 2,
    autoHideLimitLabels: false,
    showSelectionBar: true
};

const betaOptions = {
    value: 1,
    floor: 0,
    ceil: 10,
    step: 0.1,
    precision: 2,
    showTicks: false,
    autoHideLimitLabels: false,
    showSelectionBar: true
};

class LayerCorrectionsController {
    constructor(
        $rootScope,
        $state,
        $timeout,
        projectService,
        paginationService,
        colorCorrectService,
        mapService
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        this.selections = new Set();
        this.fetchScenesPage();
        this.initCorrectionHandlers();
        this.setMapLayers();
    }

    $onDestroy() {
        this.removeMapLayers();
    }

    getMap() {
        return this.mapService.getMap('project');
    }

    setMapLayers() {
        let mapLayer = this.projectService.mapLayerFromLayer(this.project, this.layer);
        return this.getMap().then(map => {
            map.setLayer(mapLayerName, mapLayer, true);
        });
    }

    removeMapLayers() {
        return this.getMap().then(map => {
            map.deleteLayers(mapLayerName);
        });
    }

    initCorrectionHandlers() {
        this.redGammaOptions = {
            ...baseGammaOptions,
            id: 'redGamma',
            onEnd: () => this.persistChanges()
        };

        this.greenGammaOptions = {
            ...baseGammaOptions,
            id: 'greenGamma',
            onEnd: () => this.persistChanges()
        };

        this.blueGammaOptions = {
            ...baseGammaOptions,
            id: 'blueGamma',
            onEnd: () => this.persistChanges()
        };

        this.singleGammaOptions = {
            ...baseGammaOptions,
            id: 'singleGamma',
            onEnd: () => {
                this.updateGammaFromProxy();
                this.persistChanges();
            }
        };

        this.saturationOptions = {
            ...baseSaturationOptions,
            id: 'saturation',
            onEnd: () => this.persistChanges()
        };

        this.alphaOptions = {
            ...alphaOptions,
            id: 'alpha',
            onEnd: () => this.persistChanges()
        };

        this.betaOptions = {
            ...betaOptions,
            id: 'beta',
            onEnd: () => this.persistChanges()
        };

        this.gammaLink = false;
    }

    ensureGammaValues() {
        this.ensureValues('gamma', ['redGamma', 'greenGamma', 'blueGamma']);
        this.updateSingleGammaProxy();
    }

    ensureSigmoidalContrastValues() {
        this.ensureValues('sigmoidalContrast', ['alpha', 'beta']);
    }

    ensureSaturationValues() {
        this.ensureValues('saturation', ['saturation']);
    }

    // avg(rgb channel values) -> single gamma value
    updateSingleGammaProxy() {
        this.singleGammaProxy = (
            (this.correction.gamma.redGamma +
                this.correction.gamma.greenGamma +
                this.correction.gamma.blueGamma) /
            3
        ).toFixed(2);
    }

    // proxy -> rgb channel values
    updateGammaFromProxy() {
        this.correction.gamma.redGamma = this.singleGammaProxy;
        this.correction.gamma.greenGamma = this.singleGammaProxy;
        this.correction.gamma.blueGamma = this.singleGammaProxy;
    }

    ensureValues(type, keys) {
        const defaults = this.colorCorrectService.getDefaultColorCorrection();
        keys.forEach(k => {
            // We use non-strict equality to match on undefined and null
            // eslint-disable-next-line eqeqeq
            if (this.correction[type][k] == null) {
                this.correction[type][k] = defaults[type][k];
            }
        });
    }

    onSceneSelect(scene) {
        const without = this.selections.filter(i => i.id !== scene.id);
        if (without.size !== this.selections.size) {
            this.selections = without;
        } else {
            this.selections = this.selections.add(scene);
        }
        if (this.selections.size === 1) {
            this.isLoadingCorrections = true;
            const baseScene = this.selections.first();
            this.colorCorrectService
                .getForLayer(baseScene.id, this.layer.id, this.project.id)
                .then(correction => {
                    this.originalCorrection = correction;
                    this.correction = _.cloneDeep(correction);
                    this.ensureGammaValues();
                    this.ensureSaturationValues();
                    this.ensureSigmoidalContrastValues();
                    this.persistChanges();
                })
                .catch(() => {})
                .finally(() => {
                    this.isLoadingCorrections = false;
                });
        }
    }

    onToggleFilter(filterType, value = !this.correction[filterType].enabled) {
        this.correction[filterType].enabled = value;
        this.persistChanges();
    }

    onToggleGammaLink(value = !this.gammaLink) {
        this.gammaLink = value;
        if (value) {
            // toggling the linked mode on
            this.updateSingleGammaProxy();
        } else {
            // toggling the linked mode off
            this.updateGammaFromProxy();
        }
        this.persistChanges();
    }

    onToggleSelection() {
        if (this.selections.size === this.pagination.count) {
            this.selections = new Set();
        } else {
            this.selections = new Set(this.sceneList);
        }
    }

    onToggleIsCorrecting(value = !this.isCorrecting) {
        this.isCorrecting = value;
    }

    onResetAll() {
        this.correction = this.colorCorrectService.getDefaultColorCorrection();
        this.persistChanges(this.sceneList);
    }

    onResetSelection() {
        this.correction = this.colorCorrectService.getDefaultColorCorrection();
        this.persistChanges();
    }

    persistChanges(scenes = this.selections.toArray()) {
        if (scenes.length) {
            this.colorCorrectService
                .bulkUpdateForLayer(this.project.id, this.layer.id, scenes, this.correction)
                .then(() => {
                    this.setMapLayers();
                });
        }
    }

    fetchScenesPage(page = this.$state.params.page || 1, filter, order) {
        // TODO do we need to list ingesting scenes? that stuff goes under filter?
        // this.getIngestingSceneCount();
        delete this.fetchError;
        this.sceneList = [];
        const currentQuery = this.projectService
            .getProjectLayerScenes(this.project.id, this.layer.id, {
                pageSize: this.projectService.scenePageSize,
                page: page - 1
            })
            .then(
                paginatedResponse => {
                    this.sceneList = paginatedResponse.results;
                    this.pagination = this.paginationService.buildPagination(paginatedResponse);
                    this.paginationService.updatePageParam(page);
                    if (this.currentQuery === currentQuery) {
                        delete this.fetchError;
                    }
                },
                e => {
                    if (this.currentQuery === currentQuery) {
                        this.fetchError = e;
                    }
                }
            )
            .finally(() => {
                if (this.currentQuery === currentQuery) {
                    delete this.currentQuery;
                }
            });
        this.currentQuery = currentQuery;
        return currentQuery;
    }

    resetCorrection() {
        const sceneIds = Array.from(this.selectedScenes.keys());
        const promise = this.colorCorrectService.bulkUpdate(
            this.projectEditService.currentProject.id,
            sceneIds
        );
        const defaultCorrection = this.colorCorrectService.getDefaultColorCorrection();
        this.setCorrection(defaultCorrection);
        this.redrawMosaic(promise, defaultCorrection);
    }
}

const component = {
    bindings: {
        project: '<',
        layer: '<'
    },
    templateUrl: tpl,
    controller: LayerCorrectionsController.name
};

export default angular
    .module('components.pages.project.layer.corrections', [])
    .controller(LayerCorrectionsController.name, LayerCorrectionsController)
    .component('rfProjectLayerCorrectionsPage', component).name;
