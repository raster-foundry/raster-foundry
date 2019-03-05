import _ from 'lodash';
import angular from 'angular';

import inputNodeTpl from './inputNode.html';
import LabActions from '_redux/actions/lab-actions';
import NodeActions from '_redux/actions/node-actions';
import { getNodeDefinition } from '_redux/node-utils';

const InputNodeComponent = {
    templateUrl: inputNodeTpl,
    controller: 'InputNodeController',
    bindings: {
        nodeId: '<'
    }
};

class InputNodeController {
    constructor(
        modalService,
        datasourceService,
        projectService,
        $q,
        $scope,
        $ngRedux,
        $log,
        $rootScope
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    mapStateToThis(state) {
        return {
            analysis: state.lab.analysis,
            previewNodes: state.lab.previewNodes,
            analysisErrors: state.lab.analysisErrors,
            node: getNodeDefinition(state, this)
        };
    }

    $onInit() {
        let unsubscribe = this.$ngRedux.connect(
            this.mapStateToThis.bind(this),
            Object.assign({}, LabActions, NodeActions)
        )(this);

        this.$scope.$on('$destroy', unsubscribe);

        this.$scope.$watch('$ctrl.node', (node, oldNode) => {
            let inputsEqual =
                node &&
                oldNode &&
                _.matches(_.pick(node, ['projId', 'band', 'layerId']))(oldNode);
            if (node && (!inputsEqual || !this.initialized)) {
                this.processUpdates();
            }
        });
    }

    processUpdates() {
        if (this.node) {
            this.initialized = true;
            if (
                this.node.projId && !this.selectedProject && !this.selectedLayer ||
                this.selectedProject && this.node.projId !== this.selectedProject.id ||
                this.selectedLayer && this.node.layerId !== this.selectedLayer.id
            ) {
                this.projectService
                    .fetchProject(this.node.projId, {
                        analysisId: this.analysis.id
                    })
                    .then(project => {
                        this.selectedProject = project;
                        const layerId = this.node.layerId || project.defaultLayerId;
                        this.projectService
                            .getProjectLayer(project.id, layerId)
                            .then(projectLayer => {
                                this.checkValidity();
                                this.selectedLayer = projectLayer;
                                this.fetchDatasources(project.id, layerId);
                            })
                            .catch(() => {});
                    });
            }

            this.selectedBand = this.node.band ? +this.node.band : this.node.band;
            this.manualBand = this.selectedBand;
            this.checkValidity();
        }
    }

    fetchDatasources(projectId, layerId) {
        if (this.selectedProject) {
            this.fetchingDatasources = true;
            this.projectService
                .getProjectLayerDatasources(projectId, layerId, {
                    analysisId: this.analysis.id
                })
                .then(datasources => {
                    this.datasources = datasources;
                    this.bands = this.datasourceService.getUnifiedBands(this.datasources);
                    this.fetchingDatasources = false;
                });
        }
    }

    firstDatasourceWithoutBands() {
        if (this.datasources) {
            return this.datasources.find(d => !d.bands.length);
        }
        return false;
    }

    checkValidity() {
        let hasError = !this.allInputsDefined();
        if (hasError && !this.analysisErrors.has(this.nodeId)) {
            this.setNodeError({
                nodeId: this.nodeId,
                error: 'Inputs must have all fields filled'
            });
        } else if (!hasError && this.analysisErrors.has(this.nodeId)) {
            this.setNodeError({
                nodeId: this.nodeId
            });
        }
    }

    selectProjectModal() {
        this.modalService
            .open({
                component: 'rfProjectSelectModal',
                resolve: {
                    project: () => this.selectedProject && this.selectedProject.id || false,
                    content: () => ({
                        title: 'Select a project',
                        nodeName: this.node.metadata.label
                    })
                }
            })
            .result.then(project => {
                this.checkValidity();
                this.updateNode({
                    payload: Object.assign({}, this.node, {
                        projId: project.id,
                        layerId: project.defaultLayerId,
                        type: 'layerSrc'
                    }),
                    hard: !this.analysisErrors.size
                });
            })
            .catch(() => {});
    }

    selectLayerModal() {
        this.modalService
            .open({
                component: 'rfProjectSelectModal',
                resolve: {
                    project: () => this.selectedProject,
                    content: () => ({
                        title: 'Select a layer from this project',
                        nodeName: this.node.metadata.label
                    }),
                    isLayerSelect: () => true
                }
            })
            .result.then(projectLayer => {
                this.checkValidity();
                this.updateNode({
                    payload: Object.assign({}, this.node, {
                        layerId: projectLayer.id,
                        type: 'layerSrc'
                    }),
                    hard: !this.analysisErrors.size
                });
            })
            .catch(() => {});
    }

    onBandChange(index) {
        this.selectedBand = index;
        if (!Number.isFinite(index) || index < 0) {
            delete this.selectedBand;
            this.manualBand = '';
        }
        this.checkValidity();
        this.updateNode({
            payload: Object.assign({}, this.node, {
                band: this.selectedBand
            }),
            hard: !this.analysisErrors.size
        });
    }

    removeBand() {
        const payload = Object.assign({}, this.node);

        delete payload.band;
        delete this.selectedBand;

        this.checkValidity();
        this.updateNode({
            payload,
            hard: !this.analysisErrors.size
        });
    }

    allInputsDefined() {
        return this.selectedProject && Number.isFinite(this.selectedBand);
    }
}

const InputNodeModule = angular.module('components.lab.inputNode', []);

InputNodeModule.component('rfInputNode', InputNodeComponent);
InputNodeModule.controller('InputNodeController', InputNodeController);

export default InputNodeModule;
