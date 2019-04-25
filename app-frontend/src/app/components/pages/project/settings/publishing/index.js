import tpl from './index.html';
import _ from 'lodash';
import { Set } from 'immutable';

class ProjectPublishingController {
    constructor(
        $rootScope,
        $q,
        $log,
        $window,
        $state,
        $timeout,
        projectService,
        tokenService,
        authService,
        paginationService
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        this.selectedLayers = new Set();
        this.layerUrls = [];
        this.tileUrl = this.projectService.getProjectTileURL(this.project);
        this.urlMappings = {
            standard: {
                label: 'Standard',
                z: 'z',
                x: 'x',
                y: 'y'
            },
            arcGIS: {
                label: 'ArcGIS',
                z: 'level',
                x: 'col',
                y: 'row'
            }
        };

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
                        layer.subtext = '';
                        if (layer.id === this.project.defaultLayerId) {
                            layer.subtext += 'Default layer';
                        }
                        if (layer.smartLayerId) {
                            layer.subtext += layer.subtext.length ? ', Smart layer' : 'Smart Layer';
                        }
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

    onSelect(layer) {
        const without = this.selectedLayers.filter(i => i.id !== layer.id);
        if (without.size !== this.selectedLayers.size) {
            this.selectedLayers = without;
        } else {
            this.selectedLayers = this.selectedLayers.add(layer);
        }
        this.updateLayerURLs();
    }

    updateMapToken() {
        return this.tokenService.getOrCreateProjectMapToken(this.project).then(t => {
            this.mapToken = t;
            return t;
        });
    }

    updateLayerURLs() {
        const zxy = (url, layer) => {
            const m = this.urlMappings.standard;
            return url
                .replace('{layerId}', layer.id)
                .replace('{z}', `{${m.z}}`)
                .replace('{x}', `{${m.x}}`)
                .replace('{y}', `{${m.y}}`);
        };
        const arcGis = (url, layer) => {
            const m = this.urlMappings.arcGIS;
            return url
                .replace('{layerId}', layer.id)
                .replace('{z}', `{${m.z}}`)
                .replace('{x}', `{${m.x}}`)
                .replace('{y}', `{${m.y}}`);
        };
        if (this.activePolicy && this.activePolicy.enum !== 'PRIVATE') {
            const layerUrl = this.projectService.getProjectLayerTileUrl(this.project, '{layerId}', {
                tag: new Date().getTime()
            });
            this.layerZXYURL = l => zxy(layerUrl, l);
            this.layerArcGISURL = l => arcGis(layerUrl, l);
        } else {
            this.updateMapToken().then(t => {
                this.mapToken = t;
                const layerUrl = this.projectService.getProjectLayerTileUrl(
                    this.project,
                    '{layerId}',
                    {
                        mapToken: this.mapToken.id,
                        tag: new Date().getTime()
                    }
                );
                this.layerZXYURL = l => zxy(layerUrl, l);
                this.layerArcGISURL = l => arcGis(layerUrl, l);
            });
        }
    }

    getLayerUrl(layer, mapping) {
        const url = this.projectService.getProjectLayerTileUrl(this.project, layer);
        const m = this.urlMappings[mapping];
        return url
            .replace('{z}', `{${m.z}}`)
            .replace('{x}', `{${m.x}}`)
            .replace('{y}', `{${m.y}}`);
    }

    isSelected(layer) {
        return this.selectedLayers.has(layer);
    }

    updateShareUrl() {
        this.projectService.getProjectShareURL(this.project).then(url => {
            this.shareUrl = url;
        });
    }

    onPolicyChange(policy) {
        let shouldUpdate = true;

        let oldPolicy = this.activePolicy;
        if (this.activePolicy) {
            this.activePolicy.active = false;
        } else {
            shouldUpdate = false;
        }

        this.activePolicy = policy;
        policy.active = true;

        this.project.tileVisibility = policy.enum;
        this.project.visibility = policy.enum;

        if (shouldUpdate) {
            if (this.project.owner.id) {
                this.project.owner = this.project.owner.id;
            }
            this.projectService.updateProject(this.project).then(
                res => {
                    this.$log.debug(res);
                },
                err => {
                    // TODO: Toast this
                    this.$log.debug('Error while updating project share policy', err);
                    this.activePolicy.active = false;
                    oldPolicy.active = true;
                    this.activePolicy = oldPolicy;
                }
            );
        }
        this.updateLayerURLs();
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
