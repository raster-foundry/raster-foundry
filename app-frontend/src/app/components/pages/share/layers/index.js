import tpl from './index.html';
import { Map, Set } from 'immutable';
import _ from 'lodash';
import L from 'leaflet';

const RED = '#E57373';
const BLUE = '#3388FF';

class ShareProjectLayersController {
    constructor(
        $rootScope,
        $state,
        $q,
        $timeout,
        mapService,
        projectService,
        paginationService,
        shareService
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }
    $onInit() {
        this.layerList = [];
        this.layerActions = new Map();
        this.visible = new Set();
        this.layerUrls = new Map();
        this.projectLayerAnnotations = {};
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
                this.toggleAnnotationCallback = this.shareService.addCallback(
                    'toggleShowingAnnotations',
                    v => this.onAnnotationsToggle(v)
                );
                this.onAnnotationsToggle(this.shareService.showingAnnotations);
            });
    }

    $onDestroy() {
        this.getMap().then(map => {
            map.deleteLayers('Layers');
            this.onAnnotationsToggle(false);
        });
        this.shareService.removeCallback('toggleShowingAnnotations', this.toggleAnnotationCallback);
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

    fetchAnnotationPage(layerId, page) {
        return this.projectService.getAnnotationsForLayer(this.project.id, layerId, {
            pageSize: 100,
            page: page - 1,
            mapToken: this.token
        });
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
            this.removeLayerAnnotations(layerId);
        } else {
            this.visible = this.visible.add(layerId);
            this.addLayerAnnotations(layerId);
        }
        this.syncMapLayersToVisible();
    }

    onAnnotationsToggle(visibility) {
        if (visibility) {
            this.visible.forEach(l => this.addLayerAnnotations(l));
        } else {
            this.visible.forEach(l => this.removeLayerAnnotations(l));
        }
    }

    addLayerAnnotations(lid) {
        if (this.shareService.showingAnnotations) {
            // fetch annotation pages until there are none left
            const f = p =>
                this.fetchAnnotationPage(lid, p).then(r => {
                    this.getMap().then(map => {
                        this.projectLayerAnnotations[lid] = this.projectLayerAnnotations[lid] || [];
                        r.features.forEach(a => {
                            this.projectLayerAnnotations[lid].push(a.id);
                            map.setGeojson(a.id, a, this.getAnnotationOptions());
                        });
                    });
                    if (r.hasNext) {
                        f(p + 1);
                    }
                });

            f(1);
        }
    }

    removeLayerAnnotations(lid) {
        this.getMap().then(map => {
            if (this.projectLayerAnnotations[lid]) {
                this.projectLayerAnnotations[lid].forEach(id => map.deleteGeojson(id));
                this.projectLayerAnnotations[lid] = [];
            }
        });
    }

    getAnnotationOptions() {
        return {
            pointToLayer: (geoJsonPoint, latlng) => {
                return L.marker(latlng, { icon: L.divIcon({ className: 'annotate-marker' }) });
            },
            onEachFeature: (feature, currentLayer) => {
                currentLayer
                    .bindPopup(
                        `
                    <label class="leaflet-popup-label">Label:<br/>
                    <p>${feature.properties.label}</p></label><br/>
                    <label class="leaflet-popup-label">Description:<br/>
                    <p>${feature.properties.description || 'No description'}</p></label>
                    `,
                        { closeButton: false }
                    )
                    .on('click', e => {
                        this.getMap().then(mapWrapper => {
                            if (this.clickedId !== feature.id) {
                                const resetPreviousLayerStyle = () => {
                                    let layer = _.first(mapWrapper.getGeojson(this.clickedId));
                                    if (layer) {
                                        this.setLayerStyle(layer, BLUE, 'annotate-marker');
                                    }
                                };
                                resetPreviousLayerStyle();
                                this.clickedId = feature.id;
                                this.setLayerStyle(e.target, RED, 'annotate-hover-marker');
                                this.$scope.$evalAsync();
                            } else {
                                delete this.clickedId;
                                this.setLayerStyle(e.target, BLUE, 'annotate-marker');
                                this.$scope.$evalAsync();
                            }
                        });
                    });
            }
        };
    }

    setLayerStyle(target, color, iconClass) {
        if (
            target.feature.geometry.type === 'Polygon' ||
            target.feature.geometry.type === 'MultiPolygon'
        ) {
            target.setStyle({ color: color });
        } else if (target.feature.geometry.type === 'Point') {
            target.setIcon(L.divIcon({ className: iconClass }));
        }
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
