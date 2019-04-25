import tpl from './index.html';
import { get } from 'lodash';

const urlMappings = {
    standard: {
        label: 'ZXY Tile Layer',
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

class MapUrlCardController {
    constructor($rootScope, $log, $q, paginationService, projectService, analysisService) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onChanges(changes) {
        const updatedProject = get(changes, 'project.currentValue');
        const updatedLayer = get(changes, 'layer.currentValue');
        const updatedMapToken = get(changes, 'mapToken.currentValue');
        const updatedSharePolicy = get(changes, 'sharePolicy.currentValue');
        const updatedShowAnalyses = get(changes, 'showAnalyses.currentValue');
        const showAnalysesUpdated = typeof updatedShowAnalyses === 'boolean';

        if (updatedProject) {
            this.project = updatedProject;
        }

        if (updatedLayer || updatedMapToken || updatedSharePolicy || showAnalysesUpdated) {
            this.updateLayerUrls(
                updatedLayer ? updatedLayer : this.layer,
                updatedMapToken ? updatedMapToken : this.mapToken,
                updatedSharePolicy ? updatedSharePolicy : this.sharePolicy
            );
            this.updateAnalysesUrls(
                updatedLayer ? updatedLayer : this.layer,
                updatedMapToken ? updatedMapToken : this.mapToken,
                updatedSharePolicy ? updatedSharePolicy : this.sharePolicy,
                showAnalysesUpdated ? updatedShowAnalyses : this.showAnalyses
            );
        }
    }

    mapTileUrl(url, mapping) {
        return url
            .replace('{z}', `{${mapping.z}}`)
            .replace('{x}', `{${mapping.x}}`)
            .replace('{y}', `{${mapping.y}}`);
    }

    updateLayerUrls(layer, mapToken, sharePolicy) {
        if (this.sharePolicy && this.sharePolicy.enum !== 'PRIVATE') {
            const layerUrl = this.projectService.getProjectLayerTileUrl(
                this.project,
                this.layer.id,
                {
                    tag: new Date().getTime()
                },
                false
            );
            this.baseLayer = {
                urls: [
                    {
                        ...urlMappings.standard,
                        url: this.mapTileUrl(layerUrl, urlMappings.standard)
                    },
                    {
                        ...urlMappings.arcGIS,
                        url: this.mapTileUrl(layerUrl, urlMappings.arcGIS)
                    }
                ],
                loading: false
            };
        } else if (this.mapToken) {
            const layerUrl = this.projectService.getProjectLayerTileUrl(
                this.project,
                this.layer,
                {
                    mapToken: this.mapToken.id,
                    tag: new Date().getTime()
                },
                false
            );
            this.baseLayer = {
                urls: [
                    Object.assign(
                        {
                            url: this.mapTileUrl(layerUrl, urlMappings.standard)
                        },
                        urlMappings.standard
                    ),
                    Object.assign(
                        {
                            url: this.mapTileUrl(layerUrl, urlMappings.arcGIS)
                        },
                        urlMappings.arcGIS
                    )
                ],
                loading: false
            };
        } else {
            this.baseLayerUrls = {
                urls: [],
                loading: true
            };
        }
    }

    updateAnalysesUrls(layer, mapToken, sharePolicy, showAnalyses) {
        const page = get(this, 'pagination.currentPage', 1);
        if (!showAnalyses) {
            this.analyses = [];
        } else if (sharePolicy.enum === 'PRIVATE') {
            this.fetchPage(this.layer, page, mapToken);
        } else {
            this.fetchPage(this.layer, page, null);
        }
    }

    fetchPage(
        layer = this.layer,
        page = 1,
        mapToken = this.sharePolicy === 'PRIVATE' ? this.mapToken : null
    ) {
        const currentPage = get(this, 'lastResponse.page');
        const currentQuery = this.$q((resolve, reject) => {
            if (currentPage === page - 1 && layer === this.layer && this.lastResponse) {
                resolve(this.lastResponse);
            } else {
                resolve(
                    this.analysisService.fetchAnalyses({
                        projectId: this.project.id,
                        projectLayerId: layer.id,
                        page: page - 1,
                        pageSize: 3
                    })
                );
            }
        })
            .then(paginatedResponse => {
                if (this.currentQuery === currentQuery) {
                    this.lastResponse = paginatedResponse;
                    this.analyses = paginatedResponse.results.map(analysis => {
                        const baseUrl = this.analysisToUrl(analysis, mapToken);
                        return {
                            id: analysis.id,
                            name: analysis.name,
                            date: analysis.modifiedAt,
                            urls: [
                                Object.assign(
                                    {
                                        url: this.mapTileUrl(baseUrl, urlMappings.standard)
                                    },
                                    urlMappings.standard
                                ),
                                Object.assign(
                                    {
                                        url: this.mapTileUrl(baseUrl, urlMappings.arcGIS)
                                    },
                                    urlMappings.arcGIS
                                )
                            ]
                        };
                    });
                    this.pagination = this.paginationService.buildPagination(paginatedResponse);
                    delete this.currentQuery;
                    delete this.queryError;
                }
            })
            .catch(e => {
                this.$log.error(e);
                if (this.currentQuery === currentQuery) {
                    this.queryError = e;
                    this.analyses = [];
                    delete this.currentQuery;
                }
            });
        this.currentQuery = currentQuery;
        return currentQuery;
    }

    analysisToUrl(analysis, mapToken) {
        if (mapToken) {
            return this.analysisService.getAnalysisTileUrlForProject(
                this.project.id,
                analysis.id,
                {
                    mapToken: mapToken.id
                },
                false
            );
        }
        return this.analysisService.getAnalysisTileUrlForProject(
            this.project.id,
            analysis.id,
            {},
            false
        );
    }
}

const component = {
    bindings: {
        project: '<',
        layer: '<',
        mapToken: '<',
        sharePolicy: '<',
        showAnalyses: '<'
    },
    templateUrl: tpl,
    controller: MapUrlCardController.name
};

export default angular
    .module('components.projects.mapUrlCard', [])
    .controller(MapUrlCardController.name, MapUrlCardController)
    .component('rfMapUrlCard', component).name;
