/* global BUILDCONFIG */
import customAnalysis from '../../../../assets/images/make-your-own-analysis.png';

class LabBrowseController {
    constructor(
        modalService, $state
    ) {
        'ngInject';
        this.BUILDCONFIG = BUILDCONFIG;
        this.$state = $state;
        this.customAnalysis = customAnalysis;
        this.modalService = modalService;
    }

    openTemplateCreateModal() {
        this.modalService.open({
            component: 'rfTemplateCreateModal'
        });
    }

    openWorkspaceCreateModal() {
        this.modalService.open({
            component: 'rfWorkspaceCreateModal'
        });
    }
}

export default angular.module('pages.lab.browse', [])
    .controller('LabBrowseController', LabBrowseController);
