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
        $rootScope, $log,
        projectService
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        this.setProjectLayerCreate();
    }

    setProjectLayerCreate() {
        this.projectLayerCreate = {
            name: '',
            projectId: this.resolve.projectId,
            colorGroupHex: '#4da687'
        };
        this.projectLayerCreateBuffer = Object.assign({}, this.projectLayerCreate);
    }

    resetModal() {
        this.isCreatingLayer = false;
        this.isCreatingLayerError = false;
        this.projectLayerCreateBuffer = Object.assign({}, this.projectLayerCreate);
    }

    isCreateDisabled() {
        return !(this.projectLayerCreateBuffer.name &&
            this.projectLayerCreateBuffer.name.length) ||
            this.isCreatingLayer ||
            this.isCreatingLayerError;
    }

    createProjectLayer() {
        this.isCreatingLayer = true;
        this.isCreatingLayerError = false;

        this.projectService
            .createProjectLayer(this.resolve.projectId, this.projectLayerCreateBuffer)
            .then(createdProjectLayer => {
                this.isCreatingLayerError = false;
                this.projectLayerCreate = Object.assign(
                    this.projectLayerCreate,
                    this.projectLayerCreateBuffer
                );
                this.close({$value: createdProjectLayer});
            })
            .catch(err => {
                this.isCreatingLayerError = true;
                this.$log.error(err);
            })
            .finally(() => {
                this.isCreatingLayer = false;
            });
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
