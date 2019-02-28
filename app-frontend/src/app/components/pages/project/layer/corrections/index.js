import { Set } from 'immutable';
import tpl from './index.html';

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
        $rootScope, $state, $timeout,
        projectService, paginationService
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        this.selections = new Set();
        this.fetchScenesPage();
        this.initCorrectionHandlers();
    }

    $onDestroy() {

    }

    initCorrectionHandlers() {
        this.redGammaOptions = {
            ...baseGammaOptions,
            id: 'redGamma',
            onEnd: (id, val) => this.onGammaFilterChange(val)
        };
        this.greenGammaOptions = {
            ...baseGammaOptions,
            id: 'greenGamma',
            onEnd: (id, val) => this.onGammaFilterChange(val)
        };
        this.blueGammaOptions = {
            ...baseGammaOptions,
            id: 'blueGamma',
            onEnd: (id, val) => this.onGammaFilterChange(val)
        };

        this.saturationOptions = {
            ...baseSaturationOptions,
            id: 'saturation',
            onEnd: () => this.onFilterChange()
        };

        this.alphaOptions = {
            ...alphaOptions,
            id: 'alpha',
            onEnd: () => this.onFilterChange()
        };

        this.betaOptions = {
            ...betaOptions,
            id: 'beta',
            onEnd: () => this.onFilterChange()
        };

        this.gammaLinkToggle = true;
    }

    addSceneActions() {

    }

    onSceneSelect(scene) {
        const without = this.selections.filter(i => i.id !== scene.id);
        if (without.size !== this.selections.size) {
            this.selections = without;
        } else {
            this.selections = this.selections.add(scene);
        }
    }

    onToggleSelection() {
        if (this.selections.size === this.pagination.count) {
            this.selections = new Set();
        } else {
            this.selections = new Set(this.sceneList);
        }
    }

    fetchScenesPage(page = this.$state.params.page || 1, filter, order) {
        // TODO do we need to list ingesting scenes? that stuff goes under filter?
        // this.getIngestingSceneCount();
        delete this.fetchError;
        this.sceneList = [];
        const currentQuery = this.projectService.getProjectLayerScenes(
            this.project.id,
            this.layer.id,
            {
                pageSize: this.projectService.scenePageSize,
                page: page - 1
            }
        ).then((paginatedResponse) => {
            this.sceneList = paginatedResponse.results;
            this.pagination = this.paginationService.buildPagination(paginatedResponse);
            this.paginationService.updatePageParam(page);
            if (this.currentQuery === currentQuery) {
                delete this.fetchError;
            }
        }, (e) => {
            if (this.currentQuery === currentQuery) {
                this.fetchError = e;
            }
        }).finally(() => {
            if (this.currentQuery === currentQuery) {
                delete this.currentQuery;
            }
        });
        this.currentQuery = currentQuery;
        return currentQuery;
    }

    toggleIsCorrecting(value = !this.isCorrecting) {
        this.isCorrecting = value;
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
    .component('rfProjectLayerCorrectionsPage', component)
    .name;
