class LabWorkspaceController {
    constructor(
        $state,
        modalService, workspaceService, analysisService
    ) {
        'ngInject';
        this.$state = $state;
        this.modalService = modalService;
        this.workspaceService = workspaceService;
        this.analysisService = analysisService;
    }
}

export default angular.module('pages.lab.workspace', [])
    .controller('LabWorkspaceController', LabWorkspaceController);
