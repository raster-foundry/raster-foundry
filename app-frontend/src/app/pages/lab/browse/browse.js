/* global BUILDCONFIG */
import customAnalysis from '../../../../assets/images/make-your-own-analysis.png';

class LabBrowseController {
    constructor(
      modalService
    ) {
        'ngInject';
        this.BUILDCONFIG = BUILDCONFIG;
        this.customAnalysis = customAnalysis;
        this.modalService = modalService;
    }

    openTemplateCreateModal() {
        this.modalService.open({
            component: 'rfTemplateCreateModal'
        });
    }
}

export default angular.module('pages.lab.browse', [])
    .controller('LabBrowseController', LabBrowseController);
