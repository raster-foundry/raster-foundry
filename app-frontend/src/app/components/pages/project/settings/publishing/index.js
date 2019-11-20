import tpl from './index.html';
import _ from 'lodash';
import { Set, Map } from 'immutable';

class ProjectPublishingController {
    constructor(
        $rootScope,
        $q,
        $log,
        $window,
        $state,
        $timeout,
        projectService,
        analysisService,
        tokenService,
        authService,
        paginationService
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        this.selectedLayers = new Set();
        this.layersToAnalyses = new Map();
        this.layerUrls = [];
        this.tileUrl = this.projectService.getProjectTileURL(this.project);
        this.showAnalyses = true;

        let sharePolicies = [
            {
                label: 'Private',
                description: `Only you and those you create tokens for
                 will be able to view tiles for this project`,
                enum: 'PRIVATE',
                active: false,
                enabled: true,
                token: true
            },
            {
                label: 'Organization',
                description: `Users in your organization will be able to use
                 their own tokens to view tiles for this project`,
                enum: 'ORGANIZATION',
                active: false,
                enabled: false,
                token: true
            },
            {
                label: 'Public',
                description: 'Anyone can view tiles for this project without a token',
                enum: 'PUBLIC',
                active: false,
                enabled: true,
                token: false
            }
        ];

        if (!this.templateTitle) {
            this.project = this.project;
            this.sharePolicies = sharePolicies.map(policy => {
                let isActive = policy.enum === this.project.tileVisibility;
                policy.active = isActive;
                return policy;
            });
            this.activePolicy = this.sharePolicies.find(policy => policy.active);
            this.updateShareUrl();
        }

        this.tileLayerUrls = {
            standard: null,
            arcGIS: null
        };

        if (_.get(this, 'activePolicy.enum') === 'PRIVATE') {
            this.updateMapToken();
        }
        this.fetchPage();
    }

    fetchPage(page = this.$state.params.page || 1) {
        this.layerList = [];
        this.layerActions = {};
        const currentQuery = this.projectService
            .getProjectLayers(this.project.id, {
                pageSize: 30,
                page: page - 1
            })
            .then(
                paginatedResponse => {
                    this.layerList = paginatedResponse.results;
                    this.layerList.forEach(layer => {
                        layer.loadingAnalyses = true;
                        this.analysisService
                            .fetchAnalyses({
                                projectId: this.project.id,
                                projectLayerId: layer.id,
                                pageSize: 0,
                                page: 0
                            })
                            .then(paginatedAnalyses => {
                                layer.loadingAnalyses = false;
                                layer.analysisCount = paginatedAnalyses.count;
                            })
                            .catch(e => {
                                this.$log.error(e);
                                layer.loadingAnalyses = false;
                            });
                    });
                    const defaultLayer = this.layerList.find(
                        l => l.id === this.project.defaultLayerId
                    );
                    this.layerActions = [];

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

    toggleShowAnalyses() {
        this.showAnalyses = !this.showAnalyses;
    }

    onSelect(layer) {
        const without = this.selectedLayers.filter(i => i.id !== layer.id);
        if (without.size !== this.selectedLayers.size) {
            this.selectedLayers = without;
        } else {
            this.selectedLayers = this.selectedLayers.add(layer);
        }
    }

    updateAnalysesMap() {
        if (!this.showAnalyses) {
            return this.$q.reject();
        }
        const selectedLayerIds = Set(this.selectedLayers.map(sl => sl.id));
        const cachedLayerIds = Set(this.layersToAnalyses.keySeq().map(l => l.id));
        const layerAnalysesToFetch = selectedLayerIds.subtract(cachedLayerIds);
        return this.$q.all(layerAnalysesToFetch.map(this.fetchLayerAnalyses.bind(this)));
    }

    updateMapToken() {
        return this.tokenService.getOrCreateProjectMapToken(this.project).then(t => {
            this.mapToken = t;
            return t;
        });
    }

    isSelected(layer) {
        return this.selectedLayers.has(layer);
    }

    updateShareUrl() {
        this.projectService.getProjectShareURL(this.project, this.mapToken).then(url => {
            this.shareUrl = url;
        });
    }

    onPolicyChange(policy, $event) {
        if (!this.updatingPolicy && this.activePolicy !== policy) {
            // TODO: Show spinner and disable checkboxes while updating
            this.updatingPolicy = true;
            if (this.project.owner.id) {
                this.project.owner = this.project.owner.id;
            }
            this.projectService
                .updateProject(
                    Object.assign({}, this.project, {
                        tileVisibility: policy.enum,
                        visibility: policy.enum
                    })
                )
                .then(() => {
                    if (this.activePolicy) {
                        this.activePolicy.active = false;
                    }
                    policy.active = true;
                    this.activePolicy = policy;
                    this.project.tileVisibility = policy.enum;
                    this.project.visibility = policy.enum;
                    if (_.get(this, 'activePolicy.enum') === 'PRIVATE') {
                        this.updateMapToken().then(() => this.updateShareUrl());
                    } else {
                        this.updateShareUrl();
                    }
                })
                .catch(e => {
                    this.$log.error('Error while updating project share policy', e);
                    this.policyError = e;
                })
                .finally(() => {
                    this.updatingPolicy = false;
                });
        }
    }

    onCopyClick(e, url, type) {
        if (url && url.length) {
            this.copyType = type;
            this.$timeout(() => {
                delete this.copyType;
            }, 1000);
        }
    }
}

const component = {
    bindings: {
        project: '<',
        tileUrl: '<',
        templateTitle: '<'
    },
    templateUrl: tpl,
    controller: ProjectPublishingController.name
};

export default angular
    .module('components.pages.projects.settings.publishing', [])
    .controller(ProjectPublishingController.name, ProjectPublishingController)
    .component('rfProjectPublishingPage', component).name;
