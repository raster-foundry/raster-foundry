import tpl from './index.html';
import { Set, Map } from 'immutable';
import { get } from 'lodash';

const mapLayerName = 'Project Layer';

class LayerScenesController {
    constructor(
        $rootScope,
        $scope,
        $state,
        projectService,
        RasterFoundryRepository,
        paginationService,
        modalService,
        authService,
        mapService
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        this.selected = new Map();
        this.permissionsPromise = this.projectService
            .getProjectPermissions(this.project, this.authService.user)
            .then(permissions => {
                this.permissions = permissions;
            });
        this.fetchPage();
        this.setMapLayers();
    }

    $onDestroy() {
        this.removeMapLayers();
    }

    getMap() {
        return this.mapService.getMap('project');
    }

    setMapLayers() {
        let mapLayer = this.projectService.mapLayerFromLayer(this.project, this.layer);
        return this.getMap().then(map => {
            map.setLayer(mapLayerName, mapLayer, true);
        });
    }

    removeMapLayers() {
        return this.getMap().then(map => {
            map.deleteLayers(mapLayerName);
        });
    }

    fetchPage(page = this.$state.params.page || 1, filter, order) {
        // TODO do we need to list ingesting scenes? that stuff goes under filter?
        // this.getIngestingSceneCount();
        delete this.fetchError;
        this.sceneList = [];
        const currentQuery = this.projectService
            .getProjectLayerScenes(this.projectId, this.layerId, {
                pageSize: this.projectService.scenePageSize,
                page: page - 1
            })
            .then(
                paginatedResponse => {
                    this.sceneList = paginatedResponse.results;
                    this.sceneActions = new Map(
                        this.sceneList.map(this.addSceneActions.bind(this))
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

    hasDownloadPermission(scene) {
        if (this.RasterFoundryRepository.getScenePermissions(scene).includes('download')) {
            return true;
        }
        return false;
    }

    addSceneActions(scene) {
        // details, view layers, hide (unapprove), remove (delete from layer)
        let actions = [
            {
                name: 'Remove',
                title: 'Remove image from layer',
                callback: () => this.removeScenes([scene]),
                menu: true
            }
        ];

        if (this.hasDownloadPermission(scene)) {
            actions.unshift({
                icon: 'icon-download',
                name: 'Download',
                title: 'Download raw image data',
                callback: () =>
                    this.modalService
                        .open({
                            component: 'rfSceneDownloadModal',
                            resolve: {
                                scene: () => scene
                            }
                        })
                        .result.catch(() => {}),
                menu: false
            });
        }

        return [scene.id, actions];
    }

    openImportModal() {
        const activeModal = this.modalService.open({
            component: 'rfSceneImportModal',
            resolve: {
                project: () => this.project,
                layer: () => this.layer,
                origin: () => 'project'
            }
        });

        activeModal.result.catch(() => {});
    }

    isSelected(scene) {
        return this.selected.has(scene.id);
    }

    onSelect(scene) {
        if (this.selected.has(scene.id)) {
            this.selected = this.selected.delete(scene.id);
        } else {
            this.selected = this.selected.set(scene.id, scene);
        }
        this.updateSelectText();
    }

    allVisibleSelected() {
        let sceneSet = new Set(this.sceneList.map(s => s.id));
        return (
            this.selected
                .keySeq()
                .toSet()
                .intersect(sceneSet).size === sceneSet.size
        );
    }

    selectAll() {
        if (this.allVisibleSelected()) {
            this.selected = this.selected.clear();
        } else {
            this.selected = this.selected.merge(
                this.sceneList.map(s => [s.id, s]).filter(s => !s.inLayer)
            );
        }
        this.updateSelectText();
    }

    updateSelectText() {
        if (this.allVisibleSelected()) {
            this.selectText = `Clear selected (${this.selected.size})`;
        } else {
            this.selectText = `Select all listed (${this.selected.size})`;
        }
    }

    browseScenes() {
        // we cannot just use this.layer here to access its AOI since it might be out of date
        this.projectService.getProjectLayer(this.projectId, this.layerId).then(resp => {
            this.layer = resp;
            if (get(this.layer, 'geometry.type')) {
                this.getMap().then(mapWrapper => {
                    const bbox = mapWrapper.map.getBounds().toBBoxString();
                    this.$state.go('project.layer.browse', { bbox });
                });
            } else {
                this.showRouteToAoiCreateModal();
            }
        });
    }

    showRouteToAoiCreateModal() {
        this.modalService
            .open({
                component: 'rfFeedbackModal',
                resolve: {
                    title: () => 'No AOI defined on this layer',
                    content: () =>
                        '<h2>Do you wish to add layer AOI?</h2>' +
                        '<p>An area of interest (AOI) is required on your layer ' +
                        'to browse scenes. Go to layer AOI creation page to add one.</p>',
                    feedbackIconType: () => 'warning',
                    feedbackIcon: () => 'icon-warning',
                    feedbackBtnType: () => 'btn-warning',
                    feedbackBtnText: () => 'Add layer AOI',
                    cancelText: () => 'Cancel'
                }
            })
            .result.then(resp => {
                this.$state.go('project.layer.aoi', {
                    layerId: this.layerId,
                    projectId: this.projectId
                });
            })
            .catch(() => {});
    }

    removeScenes(scenes) {
        // TODO remove any scenes from the map
        let sceneIds = scenes.map(s => s.id);
        // make api call
        this.projectService.removeScenesFromLayer(this.projectId, this.layerId, sceneIds).then(
            () => {
                // reset hovered scene outlines
                // re-render project layer
                let idSet = new Set(sceneIds);
                this.selected = this.selected.filterNot(scene => idSet.has(scene.id));
                this.fetchPage();
                this.removeMapLayers().then(() => this.setMapLayers());
            },
            () => {
                this.error = 'Error removing scene from project.';
            }
        );
        // remove from selected map
    }
}

const component = {
    bindings: {
        projectId: '<',
        project: '<',
        layerId: '<',
        layer: '<'
    },
    templateUrl: tpl,
    controller: LayerScenesController.name
};

export default angular
    .module('components.pages.project.scenes.page', [])
    .controller(LayerScenesController.name, LayerScenesController)
    .component('rfProjectLayerScenesPage', component).name;
