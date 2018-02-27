import _ from 'lodash';
import angular from 'angular';
import workspaceCreateModalTpl from './workspaceCreateModal.html';

const WorkspaceCreateModalComponent = {
    templateUrl: workspaceCreateModalTpl,
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    },
    controller: 'WorkspaceCreateModalController'
};

class WorkspaceCreateModalController {
    constructor(
        $state,
        workspaceService, templateService
    ) {
        'ngInject';
        this.$state = $state;
        this.workspaceService = workspaceService;
        this.templateService = templateService;
    }

    $onInit() {
        this.workspaceBuffer = Object.assign(
            {
                name: '',
                description: ''
            },
            this.resolve.template ?
                _.pick(this.resolve.template, ['name', 'description']) :
                {}
        );
    }

    createWorkspace() {
        this.currentError = '';
        this.isProcessing = true;

        let analysis = _.get(this.resolve, 'template.latestVersion.analysis');

        this.workspaceService.createWorkspace(this.workspaceBuffer).then(workspace => {
            if (analysis) {
                analysis.readonly = false;
                analysis.name = _.get(this.resolve, 'template.name');
                return this.workspaceService.addAnalysis(workspace.id, analysis).then(() => {
                    this.dismiss();
                    this.$state.go('lab.workspace', { workspaceid: workspace.id });
                    return workspace;
                });
            }
            this.dismiss();
            this.$state.go('lab.workspace', { workspaceid: workspace.id });
            return workspace;
        }, () => {
            this.currentError = 'There was an error creating the workspace.';
            this.isProcessing = false;
        });
    }

    isValid() {
        return this.workspaceBuffer.name;
    }
}

const WorkspaceeCreateModalModule = angular.module('components.lab.workspaceCreateModal', []);
WorkspaceeCreateModalModule.controller(
    'WorkspaceCreateModalController',
    WorkspaceCreateModalController
);
WorkspaceeCreateModalModule.component(
    'rfWorkspaceCreateModal',
    WorkspaceCreateModalComponent
);

export default WorkspaceeCreateModalModule;
