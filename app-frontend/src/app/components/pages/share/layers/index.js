import tpl from './index.html';
import { Map, Set } from 'immutable';
import _ from 'lodash';
import L from 'leaflet';

class ShareProjectLayersController {
    constructor($rootScope, $state, $q, $timeout, mapService, projectService, paginationService) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }
    $onInit() {
        this.layerList = [];
        this.layerActions = new Map();
        this.visible = new Set();
        this.layerUrls = new Map();
        this.copyTemplate = 'Copy tile URL';
        this.$q
            .all({
                project: this.projectPromise,
                token: this.mapToken
            })
            .then(({ project, token }) => {
                this.project = project;
                this.token = token;
                this.visible = new Set([project.defaultLayerId]);
                this.syncMapLayersToVisible();
                this.fetchPage();
            });
    }

    $onDestroy() {
        this.getMap().then(map => {
            map.deleteLayers('Layers');
        });
    }

    getMap() {
        return this.mapService.getMap('share');
    }

    fetchPage(page = this.$state.params.page || 1) {
        this.layerList = [];
        const currentQuery = this.projectService
            .getProjectLayers(this.project.id, {
                pageSize: 10,
                page: page - 1,
                mapToken: this.token
            })
            .then(
                paginatedResponse => {
                    this.layerList = paginatedResponse.results;
                    this.layerActions = new Map(
                        this.layerList.map(l => this.createLayerActions(l))
                    );
                    this.layerUrls = new Map(
                        this.layerList.map(l => [
                            l.id,
                            this.projectService.mapLayerFromLayer(this.project, l, {
                                mapToken: this.token
                            })
                        ])
                    );
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

    createLayerActions(layer) {
        const previewAction = {
            icons: [
                {
                    icon: 'icon-eye',
                    isActive: () => this.visible.has(layer.id)
                },
                {
                    icon: 'icon-eye-off',
                    isActive: () => !this.visible.has(layer.id)
                }
            ],
            name: 'Preview',
            tooltip: 'Show/hide on map',
            callback: () => this.onVisibilityToggle(layer.id),
            menu: false
        };
        const goToLayerAction = {
            icon: 'icon-map',
            name: 'View on map',
            tooltip: 'View layer on map',
            callback: () => this.viewLayerOnMap(layer),
            menu: false
        };
        const disabledGoToAction = {
            icon: 'icon-map color-light',
            name: 'View on map',
            tooltip: 'Layer does not have an area defined to go to',
            menu: false
        };
        return [
            layer.id,
            [
                previewAction,
                ...(_.get(layer, 'geometry.type') ? [goToLayerAction] : [disabledGoToAction])
            ]
        ];
    }

    onVisibilityToggle(layerId) {
        if (this.visible.has(layerId)) {
            this.visible = this.visible.delete(layerId);
        } else {
            this.visible = this.visible.add(layerId);
        }
        this.syncMapLayersToVisible();
    }

    syncMapLayersToVisible() {
        let mapLayers = this.visible
            .toArray()
            .map(layer =>
                this.projectService.mapLayerFromLayer(this.project, layer, { mapToken: this.token })
            );
        this.getMap().then(map => {
            map.setLayer('Layers', mapLayers, true);
        });
    }

    viewLayerOnMap(layer) {
        this.getMap().then(map => {
            let bounds = L.geoJSON(layer.geometry).getBounds();
            map.map.fitBounds(bounds);
            this.visible = new Set([layer.id]);
            this.syncMapLayersToVisible();
        });
    }

    onCopied() {
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
    controller: ShareProjectLayersController.name,
    templateUrl: tpl
};

export default angular
    .module('components.pages.share.project.layers', [])
    .controller(ShareProjectLayersController.name, ShareProjectLayersController)
    .component('rfShareProjectLayersPage', component).name;
