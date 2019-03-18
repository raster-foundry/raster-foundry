import tpl from './index.html';
import { Set } from 'immutable';

class ProjectEmbedController {
    constructor(
        $rootScope, $state,
        projectService, paginationService
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        this.initEmbedParameters();
        this.selectedLayers = new Set();
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

    onSelect(layer) {
        const without = this.selectedLayers.filter(i => i.id !== layer.id);
        if (without.size !== this.selectedLayers.size) {
            this.selectedLayers = without;
        } else {
            this.selectedLayers = this.selectedLayers.add(layer);
        }
        this.updateEmbedParams();
    }

    onEmbedLayerToggle(layer) {
        const without = this.embedParameters.layers.filter(i => i.id !== layer.id);
        if (without.size !== this.embedParameters.layers.size) {
            this.embedParameters.layers = without;
        } else {
            this.embedParameters.layers = this.embedParameters.layers.add(layer);
        }
        this.updateEmbedParams();
    }

    onEmbedAnalysisToggle(analysis) {
        const without = this.embedParameters.analyses.filter(i => i.id !== analysis.id);
        if (without.size !== this.embedParameters.analyses.size) {
            this.embedParameters.analyses = without;
        } else {
            this.embedParameters.analyses = this.embedParameters.analyses.add(analysis);
        }
        this.updateEmbedParams();
    }

    isSelected(layer) {
        return this.selectedLayers.has(layer);
    }

    updateEmbedParams(params) {
        this.embedParameters = {
            ...this.embedParameters,
            ...params
        };
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
