export default class ProjectItemController {
    constructor($scope, $attrs, projectService, mapService, mapUtilsService, authService,
                $uibModal) {
        'ngInject';
        this.$scope = $scope;
        this.$attrs = $attrs;
        this.projectService = projectService;
        this.mapService = mapService;
        this.mapUtilsService = mapUtilsService;
        this.authService = authService;
        this.$uibModal = $uibModal;
    }

    $onInit() {
        this.isSelectable = this.$attrs.hasOwnProperty('selectable');
        this.$scope.$watch(
            () => this.selected({project: this.project}),
            (selected) => {
                this.selectedStatus = selected;
            }
        );
        this.getMap = () => this.mapService.getMap(`${this.project.id}-map`);

        this.fitProjectExtent();
        this.addProjectLayer();
    }

    addProjectLayer() {
        let url = this.projectService.getProjectLayerURL(
            this.project,
            this.authService.token()
        );
        let layer = L.tileLayer(url);

        this.getMap().then(m => {
            m.addLayer('share-layer', layer);
        });
    }

    fitProjectExtent() {
        this.getMap().then(mapWrapper => {
            mapWrapper.map.invalidateSize();
            this.mapUtilsService.fitMapToProject(mapWrapper, this.project, -150);
        });
    }

    toggleSelected(event) {
        this.onSelect({project: this.project, selected: !this.selectedStatus});
        event.stopPropagation();
    }

    publishModal() {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        this.activeModal = this.$uibModal.open({
            component: 'rfPublishModal',
            resolve: {
                project: () => this.project,
                tileUrl: () => this.projectService.getProjectLayerURL(this.project),
                shareUrl: () => this.projectService.getProjectShareURL(this.project)
            }
        });

        return this.activeModal;
    }
    deleteModal() {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }
        this.activeModal = this.$uibModal.open({
            component: 'rfConfirmationModal',
            resolve: {
                title: () => 'Delete Project?',
                subtitle: () =>
                    'The project will be permanently deleted,'
                    + ' but scenes will be unaffected.',
                content: () =>
                    '<div class="text-center color-danger">'
                    + 'You are about to delete the project. This action is not reversible.'
                    + ' Are you sure you wish to continue?'
                    + '</div>',
                confirmText: () => 'Delete Project',
                cancelText: () => 'Cancel'
            }
        });
        this.activeModal.result.then(
            () => {
                this.projectService.deleteProject(this.projectId).then(
                    () => {
                        this.$state.go('^.^.list');
                    },
                    (err) => {
                        this.$log.debug('error deleting project', err);
                    }
                );
            });
    }
}
