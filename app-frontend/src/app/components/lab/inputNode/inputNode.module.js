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
        modalService, $scope, $ngRedux, $log,
        projectService
    ) {
        'ngInject';
        this.modalService = modalService;
        this.$scope = $scope;
        this.$log = $log;
        this.projectService = projectService;

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
                this.selectedProject &&
                this.node.projId &&
                this.node.projId !== this.selectedProject.id ||
                this.node.projId
            ) {
                this.projectService.fetchProject(this.node.projId).then(p => {
                    this.selectedProject = p;
                    this.checkValidity();
                });
            }
            this.selectedBand = +this.node.band;
            this.checkValidity();
        }
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
                this.selectedProject = project;
                this.checkValidity();
                this.updateNode({
                    payload: Object.assign({}, this.node, {
                        projId: project.id
                    }),
                    hard: !this.analysisErrors.size
                });
            });
    }

    onBandChange() {
        this.checkValidity();
        this.updateNode({
            payload: Object.assign({}, this.node, {
                band: this.selectedBand
            }),
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
