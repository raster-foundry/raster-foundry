/* global BUILDCONFIG  */
import tpl from './index.html';
import { Set } from 'immutable';
import _ from 'lodash';

class ProjectEmbedController {
    constructor(
        $rootScope, $scope, $state, $timeout,
        projectService, analysisService, paginationService, tokenService
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        this.BUILDCONFIG = BUILDCONFIG;
        this.initEmbedParameters();
        this.initMapToken();
        this.embedUrl = '';
        this.selectedLayers = new Set();
        this.layerAnalyses = {};
        this.fetchPage();
    }

    initEmbedParameters() {
        this.embedParameters = {
        // type: 'comparison' or 'single'
            projectID: this.project.id,
            type: 'comparison',
            pane1: '',
            pane2: '',
            layers: new Set(),
            analyses: new Set()
        };
    }

    initMapToken() {
        this.mapTokenRequest = this.tokenService.getOrCreateProjectMapToken(this.project);
    }

    fetchPage(page = this.$state.params.page || 1) {
        this.layerList = [];
        this.layerActions = {};
        const currentQuery = this.projectService.getProjectLayers(
            this.project.id,
            {
                pageSize: 30,
                page: page - 1
            }
        ).then(paginatedResponse => {
            this.layerList = paginatedResponse.results;
            this.layerList.forEach((layer) => {
                layer.subtext = '';
                if (layer.id === this.project.defaultLayerId) {
                    layer.subtext += 'Default layer';
                }
                if (layer.smartLayerId) {
                    layer.subtext += layer.subtext.length ? ', Smart layer' : 'Smart Layer';
                }
            });
            const defaultLayer = this.layerList.find(l => l.id === this.project.defaultLayerId);
            this.layerActions = [];

            this.pagination = this.paginationService.buildPagination(paginatedResponse);
            this.paginationService.updatePageParam(page);
            if (this.currentQuery === currentQuery) {
                delete this.fetchError;
            }
        }, e => {
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

    fetchAnalysesForLayer(layer) {
        if (!this.layerAnalyses[layer.id]) {
            this.layerAnalyses[layer.id] = {
                items: [],
                pagination: {
                    currentPage: 0
                }
            };
        }

        this.layerAnalyses[layer.id].isLoading = true;

        const currentQuery = this.analysisService.fetchAnalyses(
            {
                pageSize: 10,
                page: this.layerAnalyses[layer.id].pagination.currentPage,
                projectLayerId: layer.id
            }
        ).then((paginatedAnalyses) => {
            this.layerAnalyses[layer.id] = {
                isLoading: false,
                items: [ ...this.layerAnalyses[layer.id].items, ...paginatedAnalyses.results],
                pagination: this.paginationService.buildPagination(paginatedAnalyses)
            };
        }).catch((e) => {
            this.fetchError = e;
        });
    }

    onSelect(layer) {
        const without = this.selectedLayers.filter(i => i.id !== layer.id);
        if (without.size !== this.selectedLayers.size) {
            this.selectedLayers = without;
            this.layerAnalyses[layer.id] = false;
            this.onEmbedLayerToggle(layer, false);
        } else {
            this.selectedLayers = this.selectedLayers.add(layer);
            this.fetchAnalysesForLayer(layer);
        }
        this.updateEmbedUrl();
    }

    onEmbedLayerToggle(layer, value = !this.embedParameters.layers.has(layer)) {
        const { pane1, pane2, layers } = this.embedParameters;
        const without = layers.filter(i => i.id !== layer.id);
        if (!value) {
            this.embedParameters.layers = without;
            if (pane1 === layer.id) {
                this.embedParameters.pane1 = this.someLayerIdOrEmptyString();
            }
            if (pane2 === layer.id) {
                this.embedParameters.pane2 = this.someLayerIdOrEmptyString();
            }
        } else {
            this.embedParameters.layers = layers.add(layer);
            this.fillPanes(layer.id);
        }
        this.updateEmbedUrl();
    }

    onEmbedAnalysisToggle(analysis, value = !this.embedParameters.analyses.has(analysis)) {
        const { pane1, pane2, analyses } = this.embedParameters;
        const without = this.embedParameters.analyses.filter(i => i.id !== analysis.id);
        if (!value) {
            this.embedParameters.analyses = without;
            if (pane1 === analysis.id) {
                this.embedParameters.pane1 = this.someLayerIdOrEmptyString();
            }
            if (pane2 === analysis.id) {
                this.embedParameters.pane2 = this.someLayerIdOrEmptyString();
            }
        } else {
            this.embedParameters.analyses = this.embedParameters.analyses.add(analysis);
            this.fillPanes(analysis.id);
        }
        this.updateEmbedUrl();
    }

    onEmbedTypeChange(type) {
        this.embedParameters.type = type;
        this.fillPanes();
        this.updateEmbedUrl();
    }

    fillPanes(layerId = '') {
        const { pane1, pane2 } = this.embedParameters;
        if (!pane1) {
            this.embedParameters.pane1 = layerId || this.someLayerIdOrEmptyString();
        }
        if (!pane2) {
            this.embedParameters.pane2 = layerId || this.someLayerIdOrEmptyString();
        }
    }

    isSelected(layer) {
        return this.selectedLayers.has(layer);
    }

    hasValidParameters() {
        return !!this.embedParameters.pane1 && (
            this.embedParameters.type === 'single' || !!this.embedParameters.pane2
        );
    }

    someLayerIdOrEmptyString() {
        const { layers, analyses } = this.embedParameters;
        if (layers.size) {
            return layers.first().id;
        } else if (analyses.size) {
            return analyses.first().id;
        }
        return '';
    }

    updateEmbedUrl() {
        const base = this.BUILDCONFIG.EMBED_URI;
        if (this.hasValidParameters()) {
            this.embedParametersToString(this.embedParameters).then(p => {
                const src = `${base}?${p}`;
                // eslint-disable-next-line max-len
                this.embedUrl = `<iframe width="480" height="270" src="${src}" frameborder="0" allow=“full-screen;”></iframe>`;
                this.$scope.$evalAsync();
            });
        } else {
            this.embedUrl = '';
        }
    }

    embedParametersToString(params) {
        const paramsP = this.project.visibility !== 'PUBLIC' ?
            this.mapTokenRequest.then(m => ({ ...params, mapToken: m.id})) :
            Promise.resolve({...params});

        return paramsP.then(p => {
            if (p.type === 'single') {
                p.pane2 = '';
            }
            const kvTransform = (k, v) => `${k}=${v}`;
            const defaultTransform = k => kvTransform(k, p[k]);
            const setTransform = k => kvTransform(k, p[k].toJS().map(i => i.id).join(','));
            const setParameters = ['layers', 'analyses'];
            const stringifiedParams = Object.keys(p).map(k => setParameters.includes(k) ?
                setTransform(k) :
                defaultTransform(k)).join('&');
            return stringifiedParams;
        });
    }
}

const component = {
    bindings: {
        project: '<',
        platform: '<'
    },
    templateUrl: tpl,
    controller: ProjectEmbedController.name
};

export default angular
    .module('components.pages.projects.settings.embed', [])
    .controller(ProjectEmbedController.name, ProjectEmbedController)
    .component('rfProjectEmbedPage', component)
    .name;
