import _ from 'lodash';
import tpl from './index.html';
import {Set} from 'immutable';

class ProjectLayersPageController {
    constructor(
        $rootScope, $state,
        projectService, paginationService, modalService, authService, mapService
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        this.selected = new Set();
        this.visible = new Set([this.project.defaultLayerId]);
        this.syncMapLayersToVisible();
        this.projectService.getProjectPermissions(this.project, this.user).then(
            permissions => {
                this.permissions = permissions.map(p => p.actionType);
            });
        this.projectService.getProjectLayerStats(this.project.id).then(
            layerSceneCounts => {
                this.layerStats = layerSceneCounts;
            });
        this.fetchPage();
    }

    $onDestroy() {
        // remove layers from map
        this.getMap().then((map) => {
            map.deleteLayers('Project Layers');
        });
    }

    getMap() {
        return this.mapService.getMap('project');
    }

    getSceneCount(layerId) {
        return this.layerStats ? this.layerStats[layerId] : null;
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
        ).then((paginatedResponse) => {
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
            this.layerActions = this.layerList.map(
                (l) => this.addLayerActions(l, defaultLayer === l)
            );

            this.pagination = this.paginationService.buildPagination(paginatedResponse);
            this.paginationService.updatePageParam(page);
            if (this.currentQuery === currentQuery) {
                delete this.fetchError;
            }
        }, (e) => {
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

    addLayerActions(layer, isDefaultLayer) {
        if (!this.permissions.includes('Edit')) {
            return [];
        }
        const editAction = {
            name: 'Edit',
            callback: () => this.$state.go(
                'project.layer',
                {projectId: this.project.id, layerId: layer.id}
            ),
            menu: true
        };
        const setDefaultAction = {
            name: 'Set as default layer',
            callback: () => this.setProjectDefaultLayer(layer),
            menu: true
        };
        const publishAction = {
            name: 'Publishing',
            callback: () => this.$state.go(
                'project.layer.settings.publishing',
                {projectId: this.project.id, layerId: layer.id}
            ),
            menu: true
        };
        const exportAction = {
            name: 'Export',
            callback: () => this.$state.go(
                'project.layer.settings.publishing',
                {projectId: this.project.id, layerId: layer.id}
            ),
            menu: true
        };
        const settingsAction = {
            name: 'Settings',
            callback: () => this.$state.go(
                'project.layer.settings',
                {projectId: this.project.id, layerId: layer.id}
            ),
            menu: true
        };
        const deleteAction = {
            name: 'Delete',
            callback: () => this.deleteProjectLayer(layer),
            menu: true
        };
        const section = {
            section: true
        };

        const unimplementedActions = [publishAction, exportAction, settingsAction];
        const layerActions = [editAction];
        if (!isDefaultLayer) {
            layerActions.push(setDefaultAction);
        }

        return [
            editAction,
            ...!isDefaultLayer ? [setDefaultAction, deleteAction] : []
        ];
    }

    onSelect(layerId) {
        if (this.selected.has(layerId)) {
            this.selected = this.selected.delete(layerId);
        } else {
            this.selected = this.selected.add(layerId);
        }
    }

    isSelected(layerId) {
        return this.selected.has(layerId);
    }

    onHide(layerId) {
        if (this.visible.has(layerId)) {
            this.visible = this.visible.delete(layerId);
        } else {
            this.visible = this.visible.add(layerId);
        }
        this.syncMapLayersToVisible();
    }

    isVisible(layerId) {
        return this.visible.has(layerId);
    }

    setProjectDefaultLayer(layer) {
        this.projectService.updateProject(Object.assign({}, this.project, {
            defaultLayerId: layer.id
        })).then(() => {
            this.$state.go('.', {}, {inherit: true, reload: true});
        });
    }

    deleteProjectLayer(layer) {
        const modal = this.modalService.open({
            component: 'rfFeedbackModal',
            resolve: {
                title: () => 'Really delete layer?',
                subtitle: () => 'Deleting layers cannot be undone',
                content: () =>
                    '<h2>Do you wish to continue?</h2>'
                    + '<p>Future attempts to access this '
                    + 'layer or associated annotations, tiles, and scenes will fail.',
                feedbackIconType: () => 'danger',
                feedbackIcon: () => 'icon-warning',
                feedbackBtnType: () => 'btn-danger',
                feedbackBtnText: () => 'Delete layer',
                cancelText: () => 'Cancel'
            }
        });
        modal.result.then(() => {
            this.projectService.deleteProjectLayer(this.project.id, layer.id).then(() => {
                this.fetchPage();
            });
        });
    }

    showDefaultLayer() {
        this.visible = new Set([this.project.defaultLayerId]);
        this.syncMapLayersToVisible();
    }

    showPageLayers() {
        this.visible = this.visible.union(this.layerList.map(l => l.id));
        this.syncMapLayersToVisible();
    }

    onLayerFocus(layer) {
        if (layer) {
            this.focusedLayer = layer;
        } else {
            delete this.focusedLayer;
        }
    }

    mapLayerFromLayer(layer) {
        let url = this.projectService.getProjectLayerTileUrl(
            this.project, layer, {token: this.authService.token()}
        );
        let mapLayer = L.tileLayer(url, {
            maxZoom: 30
        });
        return mapLayer;
    }

    syncMapLayersToVisible() {
        // TODO do this more efficiently (don't re-add existing layers)
        let mapLayers = this.visible.toArray().map(this.mapLayerFromLayer.bind(this));
        this.getMap().then(map => {
            map.setLayer('Project Layers', mapLayers, true);
        });
    }

    showNewLayerModal() {
        const modal = this.modalService.open({
            component: 'rfProjectLayerCreateModal',
            resolve: {
                projectId: () => this.project.id
            }
        });

        modal.result
            .then(() => this.fetchPage())
            .catch(() => {});
    }
}

const component = {
    bindings: {
        user: '<',
        userRoles: '<',
        project: '<'
    },
    templateUrl: tpl,
    controller: ProjectLayersPageController.name
};

export default angular
    .module('components.pages.projects.layers', [])
    .controller(ProjectLayersPageController.name, ProjectLayersPageController)
    .component('rfProjectLayersPage', component)
    .name;
