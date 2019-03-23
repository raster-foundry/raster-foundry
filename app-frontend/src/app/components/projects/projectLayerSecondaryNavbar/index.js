import tpl from './index.html';

class ProjectLayerSecondaryNavbarController {
    constructor($rootScope, $state, $scope, $log, modalService, projectService) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        this.projectId = this.$state.params.projectId;
        this.layerId = this.$state.params.layerId;
        this.setLayerActions();
    }

    setLayerActions() {
        const splitLayer = {
            name: 'Split layer',
            title: 'split-layer',
            menu: true,
            separator: true,
            callback: () => this.openSplitLayerModal()
        };

        const deleteLayer = {
            name: 'Delete layer',
            title: 'delete-layer',
            menu: true,
            callback: () => this.openLayerDeleteModal()
        };

        this.actions = [splitLayer, deleteLayer];
    }

    openSplitLayerModal() {
        const modal = this.modalService.open({
            component: 'rfLayerSplitModal',
            resolve: {
                projectId: () => this.projectId,
                layerId: () => this.layerId
            }
        });

        modal.result.then(() => this.fetchPage()).catch(() => {});
    }

    openLayerDeleteModal() {
        const modal = this.modalService.open({
            component: 'rfFeedbackModal',
            resolve: {
                title: () => 'Are you sure you want to delete this layer?',
                subtitle: () => 'Deleting a layer cannot be undone',
                content: () =>
                    '<h2>Do you wish to continue?</h2>' +
                    '<p>Future attempts to access this ' +
                    'layer or associated annotations, tiles, and scenes will fail.',
                feedbackIconType: () => 'danger',
                feedbackIcon: () => 'icon-warning',
                feedbackBtnType: () => 'btn-danger',
                feedbackBtnText: () => 'Delete layer',
                cancelText: () => 'Cancel'
            }
        }).result;

        modal
            .then(() => {
                this.projectService
                    .deleteProjectLayer(this.projectId, this.layerId)
                    .then(() => {
                        this.$state.go('project.layers', { projectId: this.projectId });
                    })
                    .catch(e => {
                        this.$window.alert(
                            'There was an error deleting this layer. Please try again later.'
                        );
                        this.$log.error(e);
                    });
            })
            .catch(() => {});
    }
}

const component = {
    bindings: {},
    templateUrl: tpl,
    controller: ProjectLayerSecondaryNavbarController.name
};

export default angular
    .module('components.projects.projectLayerSecondaryNavbar', [])
    .controller(ProjectLayerSecondaryNavbarController.name, ProjectLayerSecondaryNavbarController)
    .component('rfProjectLayerSecondaryNavbar', component).name;
