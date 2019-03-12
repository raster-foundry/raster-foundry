import tpl from './index.html';
import _ from 'lodash';
import { Set } from 'immutable';

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
        const currentQuery = this.analysisService
            .fetchAnalyses({
                pageSize: 30,
                page: page - 1,
                mapToken: this.token,
                projectId: this.project.id
            })
            .then(
                paginatedResponse => {
                    this.analysisList = paginatedResponse.results;
                    this.analysisActions = new Map(
                        this.analysisList.map(l => this.createAnalysisActions(l))
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
        const copyTileUrlAction = {
            icon: 'icon-copy',
            name: 'Copy Url',
            tooltip: 'Copy Tile Url',
            callback: () => this.copyUrl(analysis),
            menu: false
        };
        return [
            analysis.id,
            [
                previewAction,
                ...(_.get(analysis, 'geometry.type') ? [goToAnalysisAction] : []),
                copyTileUrlAction
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
            // TODO get geometry from mask
            console.log('TODO: get analysis geometry and zoom to it');
            let bounds = L.geoJSON(analysis.geometry).getBounds();
            map.map.fitBounds(bounds);
            this.visible = new Set([analysis.id]);
            this.syncMapLayersToVisible();
        });
    }

    copyTileUrl(analysis) {
        // TODO: Implement
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
