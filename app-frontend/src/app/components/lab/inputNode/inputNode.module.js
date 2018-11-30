/* globals _ */

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
        modalService, datasourceService, projectService, sceneService,
        $q, $scope, $ngRedux, $log
    ) {
        'ngInject';
        this.modalService = modalService;
        this.datasourceService = datasourceService;
        this.projectService = projectService;
        this.sceneService = sceneService;
        this.$scope = $scope;
        this.$log = $log;
        this.$q = $q;

        let unsubscribe = $ngRedux.connect(
            this.mapStateToThis.bind(this),
            Object.assign({}, LabActions, NodeActions)
        )(this);
        $scope.$on('$destroy', unsubscribe);

        $scope.$watch('$ctrl.node', (node, oldNode) => {
            let inputsEqual = node && oldNode && (
                (a, b) => a.projId === b.projId && a.band === b.band
            )(node, oldNode);
            if (node && (!inputsEqual || !this.initialized)) {
                this.processUpdates();
            }
        });
    }

    mapStateToThis(state) {
        return {
            analysis: state.lab.analysis,
            previewNodes: state.lab.previewNodes,
            analysisErrors: state.lab.analysisErrors,
            node: getNodeDefinition(state, this)
        };
    }

    processUpdates() {
        if (this.node) {
            this.initialized = true;
            if (
                this.node.projId &&
                !this.selectedProject ||
                this.selectedProject &&
                this.node.projId !== this.selectedProject.id
            ) {
                this.projectService.fetchProject(this.node.projId).then(p => {
                    this.selectedProject = p;
                    this.fetchDatasources(p.id);
                    this.checkValidity();
                });
            }

            this.selectedBand = this.node.band ?
                +this.node.band :
                this.node.band;
            this.checkValidity();
        }
    }

    fetchDatasources(projectId) {
        if (this.selectedProject) {
            this.fetchingDatasources = true;
            this.projectService.getProjectDatasources(projectId).then(
                datasources => {
                    this.datasources = datasources;
                    this.bands = this.datasourceService.getUnifiedBands(this.datasources);
                    this.fetchingDatasources = false;
                }
            );
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
                    content: () => ({title: 'Select a project'})
                }
            }).result.then(project => {
                this.checkValidity();
                this.updateNode({
                    payload: Object.assign({}, this.node, {
                        projId: project.id
                    }),
                    hard: !this.analysisErrors.size
                });
            });
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
