import tpl from './index.html';
import { OrderedMap, Set } from 'immutable';
import _ from 'lodash';

const mapLayerName = 'Project Layer';
const shapeLayer = 'Export AOI';
const deleteActions = ['*', 'DELETE'];
const exportActions = ['*', 'VIEW', 'EDIT', 'ANNOTATE', 'DELETE', 'EXPORT', 'DOWNLOAD'];

class LayerExportsController {
    constructor(
        $rootScope, $scope, $state, $log,
        moment, projectService, paginationService, modalService, authService,
        mapService, exportService
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        this.selected = new OrderedMap();
        this.visible = new Set();
        this.exportList = [];
        this.currentQuery = this.getPermissions().then(() => this.fetchPage());
        this.setMapLayers();
    }

    $onDestroy() {
        this.removeMapLayers();
        this.removeExportAois();
    }

    getPermissions() {
        return this.projectService
            .getAllowedActions(this.project.id)
            .then((actions) => {
                const deleteAllowed = _.intersection(actions, deleteActions).length;
                const exportAllowed = _.intersection(actions, exportActions).length;
                this.actionPermissions = {
                    delete: deleteAllowed,
                    export: exportAllowed
                };
            });
    }

    fetchPage(page = this.$state.params.page || 1) {
        delete this.fetchError;
        this.exportList = [];
        this.exportActions = new OrderedMap();
        const currentQuery = this.projectService.listExports(
            {
                sort: 'createdAt,desc',
                pageSize: '10',
                page: page - 1,
                project: this.project.id,
                layer: this.layer.id
            }
        ).then((paginatedResponse) => {
            this.exportList = paginatedResponse.results;
            this.exportActions = new OrderedMap(
                this.exportList.map(expt => [expt.id, this.createExportActions(expt)])
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
        this.getMap().then(map => {
            map.deleteLayers(mapLayerName);
        });
    }

    removeExportAois() {
        this.getMap().then(map => {
            map.deleteGeojson(shapeLayer);
        });
    }

    createExportActions(exportItem) {
        const previewAction = {
            icons: [
                {
                    icon: 'icon-eye',
                    isActive: () => this.visible.has(exportItem.id)
                }, {
                    icon: 'icon-eye-off',
                    isActive: () => !this.visible.has(exportItem.id)
                }
            ],
            name: 'Preview',
            tooltip: 'Show/hide on bounds on map',
            callback: () => this.onHide(exportItem.id),
            menu: false
        };
        const downloadAction = {
            icon: 'icon-download',
            name: 'Download',
            tooltip: 'Download',
            callback: () => this.onDownloadExport(exportItem.id),
            menu: false
        };
        const deleteAction = {
            name: 'Delete export',
            callback: () => this.deleteExports([exportItem]),
            menu: true
        };
        const defaultActions = [previewAction];
        return [
            ...defaultActions,
            ...exportItem.exportStatus === 'EXPORTED' ? [downloadAction] : [],
            ...this.actionPermissions.delete ? [deleteAction] : []
        ];
    }

    deleteExports(exports) {
        const isMultiple = exports.length > 1;
        const modal = this.modalService.open({
            component: 'rfFeedbackModal',
            resolve: {
                title: () => `Delete ${isMultiple ? 'these exports' : 'this export'}?`,
                subtitle: () => `Deleting ${isMultiple ? 'these exports' : 'this export'}`
                    + ' cannot be undone',
                content: () =>
                    '<h2>Do you wish to continue?</h2>'
                    + `<p>Future attempts to access ${isMultiple ? 'these exports' : 'this export'}`
                    + ' will fail.</p>',
                feedbackIconType: () => 'danger',
                feedbackIcon: () => 'icon-warning',
                feedbackBtnType: () => 'btn-danger',
                feedbackBtnText: () => `Delete ${isMultiple ? 'exports' : 'export'}`,
                cancelText: () => 'Cancel'
            }
        });
        modal.result.then(() => {
            this.exportService.deleteExports(exports.map(e => e.id))
                .then(() => {
                    this.fetchPage();
                    this.visible = this.visible.substract(this.selected.keySeq());
                    this.syncGeoJsonLayersToVisible(this.visible.toArray());
                    this.selected = new Map();
                })
                .catch(() => {});
        });
    }

    createItemInfo(expt) {
        return {
            id: expt.id,
            name: this.formatDateDisplay(expt.createdAt),
            subtext: expt.subtext,
            date: expt.createdAt,
            colorGroupHex: this.layer.colorGroupHex,
            exportStatus: expt.exportStatus
        };
    }

    formatDateDisplay(date) {
        return date.length ? this.moment.utc(date).format('LL') + ' (UTC)' : 'MM/DD/YYYY';
    }

    isVisible(id) {
        return this.visible.has(id);
    }

    onHide(id) {
        if (this.visible.has(id)) {
            this.visible = this.visible.delete(id);
        } else {
            this.visible = this.visible.add(id);
        }
        this.syncGeoJsonLayersToVisible(this.visible.toArray());
    }

    syncGeoJsonLayersToVisible(visibleExportIds) {
        const features = this.getFeaturesFromExports(visibleExportIds);
        if (features.length) {
            this.getMap().then(map => {
                map.addGeojson(shapeLayer, {
                    type: 'FeatureCollection',
                    features: _.compact(features)
                }, true);
            });
        } else {
            this.removeExportAois();
        }
    }

    getFeaturesFromExports(exportIds) {
        this.removeExportAois();

        const features = this.exportList.map(e => {
            if (exportIds.includes(e.id)) {
                return {
                    'geometry': e.exportOptions.mask,
                    'properties': {},
                    'type': 'Feature'
                };
            }
            return 0;
        });
        return _.compact(features);
    }

    onDownloadExport(id) {
        this.openDownloadModal(this.exportList.find(e => e.id === id));
    }

    openDownloadModal(expt) {
        this.modalService.open({
            component: 'rfExportDownloadModal',
            resolve: {
                export: () => expt
            }
        }).result.catch(() => {});
    }

    selectAll() {
        if (this.allVisibleSelected()) {
            this.selected = new OrderedMap();
        } else {
            this.selected = this.selected.merge(
                new OrderedMap(this.exportList.map(e => [e.id, e]))
            );
        }
        this.updateSelectText();
    }

    onSelect(id) {
        if (this.selected.has(id)) {
            this.selected = this.selected.delete(id);
        } else {
            this.selected = this.selected.set(id, this.exportList.find(e => e.id === id));
        }
        this.updateSelectText();
    }


    isSelected(id) {
        return this.selected.has(id);
    }

    allVisibleSelected() {
        const exportSet = new Set(this.exportList.map(l => l.id));
        return exportSet.intersect(this.selected.keySeq()).size === exportSet.size;
    }

    updateSelectText() {
        if (this.allVisibleSelected()) {
            this.selectText = `Clear selected (${this.selected.size})`;
        } else {
            this.selectText = `Select all listed (${this.selected.size})`;
        }
    }
}

const component = {
    bindings: {
        project: '<',
        layer: '<'
    },
    templateUrl: tpl,
    controller: LayerExportsController.name
};

export default angular
    .module('components.pages.project.layer.exports', [])
    .controller(LayerExportsController.name, LayerExportsController)
    .component('rfProjectLayerExportsPage', component)
    .name;
