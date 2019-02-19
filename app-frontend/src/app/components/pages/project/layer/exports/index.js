import tpl from './index.html';
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
        this.selected = new Map([]);
        this.visible = new Set([]);
        this.fetchPage();
        this.setMapLayers();
    }

    $onDestroy() {
        this.removeMapLayers();
        this.removeExportAois();
    }

    fetchPage(page = this.$state.params.page || 1) {
        delete this.fetchError;
        this.exportList = [];
        this.itemList = [];
        this.itemActions = [];
        const currentQuery = this.projectService.listExports(
            {
                sort: 'createdAt,desc',
                pageSize: '10',
                page: page - 1,
                project: this.project.id,
                layer: this.layer.id
            }
        ).then((paginatedResponse) => {
            this.projectService
                .getAllowedActions(this.project.id)
                .then((actions) => {
                    this.isDeleteAllowed = _.intersection(actions, deleteActions).length;
                    this.isExportAllowed = _.intersection(actions, exportActions).length;

                    this.exportList = paginatedResponse.results;
                    this.exportList.forEach(e => {
                        e.subtext = '';
                    });
                    this.itemList = this.exportList.map(expt => {
                        this.getExportActions(expt);
                        return this.createItemInfo(expt);
                    });
                    this.pagination = this.paginationService.buildPagination(paginatedResponse);
                    this.paginationService.updatePageParam(page);
                    if (this.currentQuery === currentQuery) {
                        delete this.fetchError;
                    }
                });
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

    getExportActions(expt) {
        if (!this.isDeleteAllowed) {
            this.itemActions.push([]);
        } else {
            this.itemActions.push(this.createItemActions(expt));
        }
    }

    createItemActions(expt) {
        const deleteAction = {
            name: 'Delete export',
            callback: () => this.deleteExports([expt]),
            menu: true
        };
        return [deleteAction];
    }

    selectedToArray() {
        return Array.from(this.selected.values());
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
                .then(() => this.fetchPage())
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
            this.visible.delete(id);
        } else {
            this.visible.add(id);
        }
        this.syncGeoJsonLayersToVisible(Array.from(this.visible));
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
            // this.selected.clear();
            this.selected = new Map([]);
        } else {
            this.selected = new Map(this.exportList.map(e => [e.id, e]));
        }
        this.updateSelectText();
    }

    onSelect(id) {
        if (this.selected.has(id)) {
            this.selected.delete(id);
        } else {
            this.selected.set(id, this.exportList.find(e => e.id === id));
        }
        this.updateSelectText();
    }


    isSelected(id) {
        return this.selected.has(id);
    }

    allVisibleSelected() {
        const exportSet = new Map(this.exportList.map(e => [e.id, e]));
        return exportSet.size === this.selected.size;
    }

    updateSelectText() {
        if (this.allVisibleSelected()) {
            this.selectText = 'Clear selected';
        } else {
            this.selectText = 'Select all listed';
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
