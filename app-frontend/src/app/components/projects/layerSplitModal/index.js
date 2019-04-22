import angular from 'angular';
import { get, uniqBy } from 'lodash';

import tpl from './index.html';

const defaultLayerSplit = {
    period: 'DAY',
    splitOnDatasource: true,
    removeFromLayer: true
};

const frequencies = [
    {
        value: 'WEEK',
        name: '1 week'
    },
    {
        value: 'DAY',
        name: '1 day'
    }
];

const dateDisplayFormat = 'MMMM Do, YYYY';

class LayerSplitModalController {
    constructor($rootScope, $log, $q, $state, moment, projectService, modalService) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        this.projectId = this.resolve.projectId;
        this.layerId = this.resolve.layerId;
        this.defaultLayerSplit = defaultLayerSplit;
        this.frequencies = frequencies;
        this.dateDisplayFormat = dateDisplayFormat;
        this.layerSplitBuffer = {};

        this.populateLayerSplitBuffer();
        this.getLayerDatasources();
    }

    populateLayerSplitBuffer() {
        const promises = {
            layer: this.projectService.getProjectLayer(this.projectId, this.layerId),
            firstScene: this.projectService.getProjectLayerScenes(this.projectId, this.layerId, {
                page: 0,
                pageSize: 1,
                sort: 'acquisitionDatetime,asc'
            }),
            lastScene: this.projectService.getProjectLayerScenes(this.projectId, this.layerId, {
                page: 0,
                pageSize: 1,
                sort: 'acquisitionDatetime,desc'
            })
        };
        this.$q.all(promises).then(({ layer, firstScene, lastScene }) => {
            if (!firstScene.count) {
                this.noScene = true;
                return;
            }
            this.layerSplitBuffer = Object.assign(
                {},
                {
                    colorGroupHex: layer.colorGroupHex,
                    rangeStart: get(firstScene, 'results[0].filterFields.acquisitionDate'),
                    rangeEnd: get(lastScene, 'results[0].filterFields.acquisitionDate')
                },
                this.defaultLayerSplit
            );
            this.setFormattedDate();
            this.validateDateRange();
        });
    }

    isSplitLayerDisabled() {
        return (
            this.noScene ||
            !get(this, 'layerSplitBuffer.name.length') ||
            this.isSplittingLayer ||
            !this.isValidDateRange ||
            this.hasSplitLayerError
        );
    }

    setFormattedDate() {
        this.startDateDisplay = this.moment(this.layerSplitBuffer.rangeStart).format(
            this.dateDisplayFormat
        );
        this.endDateDisplay = this.moment(this.layerSplitBuffer.rangeEnd).format(
            this.dateDisplayFormat
        );
    }

    onDateChange(rangeType) {
        const modal = this.modalService.open(
            {
                component: 'rfDatePickerModal',
                windowClass: 'auto-width-modal',
                resolve: {
                    config: () =>
                        Object({
                            selectedDay: this.layerSplitBuffer[rangeType]
                        })
                }
            },
            false
        ).result;

        modal.then(selectedDate => {
            this.layerSplitBuffer[rangeType] = selectedDate.toISOString();
            this.setFormattedDate();
            this.validateDateRange();
        });
    }

    validateDateRange() {
        this.isValidDateRange =
            this.moment(this.layerSplitBuffer.rangeEnd).diff(
                this.moment(this.layerSplitBuffer.rangeStart)
            ) >= 0;
    }

    onCheckRemoveImages() {
        this.layerSplitBuffer.removeFromLayer = !this.layerSplitBuffer.removeFromLayer;
    }

    getLayerDatasources() {
        this.projectService
            .getProjectLayerDatasources(this.projectId, this.layerId)
            .then(datasources => {
                this.hasMultipleDatasources = get(uniqBy(datasources, 'id'), 'length') > 1;
            });
    }

    onClickSplit() {
        this.isSplittingLayer = true;
        this.projectService
            .splitLayers(this.projectId, this.layerId, this.layerSplitBuffer)
            .then(resp => {
                this.$state.go('project.layers', { projectId: this.projectId });
                this.dismiss();
            })
            .catch(err => {
                this.$log.error(err);
                this.hasSplitLayerError = true;
            })
            .finally(() => {
                this.isSplittingLayer = false;
            });
    }

    resetModal() {
        this.hasSplitLayerError = false;
        this.populateLayerSplitBuffer();
    }
}

const LayerSplitModalComponent = {
    templateUrl: tpl,
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    },
    controller: 'LayerSplitModalController'
};

export default angular
    .module('components.projects.layerSplitModal', [])
    .controller('LayerSplitModalController', LayerSplitModalController)
    .component('rfLayerSplitModal', LayerSplitModalComponent).name;
