import angular from 'angular';
import tpl from './projectLayerCreateModal.html';

const ProjectLayerCreateModalComponent = {
    templateUrl: tpl,
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    },
    controller: 'ProjectLayerCreateModalController'
};

class ProjectLayerCreateModalController {
    constructor(
    ) {
        'ngInject';
    }

    $onInit() {
    }
}

const ProjectLayerCreateModalModule = angular.module(
    'components.projects.projectLayerCreateModal', []
);

ProjectLayerCreateModalModule.controller(
    'ProjectLayerCreateModalController', ProjectLayerCreateModalController
);

ProjectLayerCreateModalModule.component(
    'rfProjectLayerCreateModal', ProjectLayerCreateModalComponent
);

export default ProjectLayerCreateModalModule;
