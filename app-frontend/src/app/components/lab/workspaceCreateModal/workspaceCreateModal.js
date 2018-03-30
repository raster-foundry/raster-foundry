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
        workspaceService
    ) {
        'ngInject';
        this.$state = $state;
        this.workspaceService = workspaceService;
    }

    $onInit() {
        this.workspaceBuffer = {
            name: '',
            description: ''
        };
    }

    createWorkspace() {
        this.currentError = '';
        this.isProcessing = true;
        this.workspaceService.createWorkspace(this.workspaceBuffer).then(workspace => {
            this.dismiss();
            this.$state.go('lab.workspace', { workspaceid: workspace.id });
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
