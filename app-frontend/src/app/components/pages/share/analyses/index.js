import tpl from './index.html';
import _ from 'lodash';
import { Map, Set } from 'immutable';

class ShareProjectAnalysesController {
    constructor(
        $rootScope,
        $state,
        $q,
        mapService,
        projectService,
        analysisService,
        paginationService
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        this.analysisActions = new Map();
        this.visible = new Set();
        this.tileUrls = new Map();
        this.copyTemplate = 'Copy tile URL';
        this.$q
            .all({
                project: this.projectPromise,
                token: this.mapToken
            })
            .then(({ project, token }) => {
                this.project = project;
                this.token = token;
                this.fetchPage();
            });
    }

    $onDestroy() {
        this.getMap().then(map => {
            map.deleteLayers('Analyses');
        });
    }

    getMap() {
        return this.mapService.getMap('share');
    }

    fetchPage(page = this.$state.params.page || 1) {
        this.analysisList = [];
        const currentQuery = this.projectService
            .getProjectAnalyses(this.project.id, {
                pageSize: 10,
                page: page - 1,
                mapToken: this.token
            })
            .then(
                paginatedResponse => {
                    this.analysisList = paginatedResponse.results;
                    this.analysisActions = new Map(
                        this.analysisList.map(a => this.createAnalysisActions(a))
                    );
                    this.tileUrls = new Map(
                        this.analysisList.map(a => [
                            a.id,
                            this.analysisService.getAnalysisTileUrlForProject(
                                this.project.id,
                                a.id,
                                {
                                    mapToken: this.token
                                }
                            )
                        ])
                    );
                    if (this.visible.size === 0 && this.analysisList.length) {
                        this.onVisibilityToggle(this.analysisList[0].id);
                    }
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

    createAnalysisActions(analysis) {
        const previewAction = {
            icons: [
                {
                    icon: 'icon-eye',
                    isActive: () => this.visible.has(analysis.id)
                },
                {
                    icon: 'icon-eye-off',
                    isActive: () => !this.visible.has(analysis.id)
                }
            ],
            name: 'Preview',
            tooltip: 'Show/hide on map',
            callback: () => this.onVisibilityToggle(analysis.id),
            menu: false
        };
        const goToAnalysisAction = {
            icon: 'icon-map',
            name: 'View on map',
            tooltip: 'View layer on map',
            callback: () => this.viewAnalysisOnMap(analysis),
            menu: false
        };
        const disabledGoToAction = {
            icon: 'icon-map color-light',
            name: 'View on map',
            tooltip: 'Analysis does not have an area defined to go to',
            menu: false
        };
        return [
            analysis.id,
            [
                previewAction,
                ...(_.get(analysis, 'layerGeometry.type')
                    ? [goToAnalysisAction]
                    : [disabledGoToAction])
            ]
        ];
    }

    onVisibilityToggle(analysisId) {
        if (this.visible.has(analysisId)) {
            this.visible = this.visible.delete(analysisId);
        } else {
            this.visible = this.visible.add(analysisId);
        }
        this.syncMapLayersToVisible();
    }

    syncMapLayersToVisible() {
        let mapLayers = this.visible.toArray().map(analysisId => {
            const tileUrl = this.analysisService.getAnalysisTileUrlForProject(
                this.project.id,
                analysisId,
                {
                    mapToken: this.token
                }
            );
            return L.tileLayer(tileUrl, { maxZoom: 30 });
        });
        this.getMap().then(map => {
            map.setLayer('Analyses', mapLayers, true);
        });
    }

    viewAnalysisOnMap(analysis) {
        this.getMap().then(map => {
            let bounds = L.geoJSON(analysis.layerGeometry).getBounds();
            map.map.fitBounds(bounds);
            this.visible = new Set([analysis.id]);
            this.syncMapLayersToVisible();
        });
    }

    onCopied() {
        this.$log.log('Url copied');
        this.copyTemplate = 'Copied';
        this.$timeout(() => {
            this.copyTemplate = 'Copy tile URL';
        }, 1500);
    }
}

const component = {
    bindings: {
        mapToken: '<',
        projectPromise: '<'
    },
    controller: ShareProjectAnalysesController.name,
    templateUrl: tpl
};

export default angular
    .module('components.pages.share.project.analyses', [])
    .controller(ShareProjectAnalysesController.name, ShareProjectAnalysesController)
    .component('rfShareProjectAnalysesPage', component).name;
