import projectPlaceholder from '../../../../assets/images/transparent.svg';

export default class ProjectItemController {
    constructor($scope, $state, $attrs, $log, projectService, mapService, mapUtilsService,
                authService, $uibModal) {
        'ngInject';
        this.$scope = $scope;
        this.$state = $state;
        this.$attrs = $attrs;
        this.projectService = projectService;
        this.mapService = mapService;
        this.mapUtilsService = mapUtilsService;
        this.authService = authService;
        this.$uibModal = $uibModal;
        this.$log = $log;

        this.projectPlaceholder = projectPlaceholder;
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
        this.getProjectStatus();
        this.getThumbnailURL();
    }

    getThumbnailURL() {
        this.thumbnailUrl = this.projectService.getProjectThumbnailURL(
            this.project, this.authService.token()
        );
    }

    addProjectLayer() {
        let url = this.projectService.getProjectLayerURL(
            this.project,
            this.authService.token()
        );

        const tileLayerOptions = {maxZoom: 30};
        let layer = L.tileLayer(url, tileLayerOptions);

        this.getMap().then(m => {
            m.addLayer('share-layer', layer);
        });
    }

    fitProjectExtent() {
        this.getMap().then(mapWrapper => {
            this.mapUtilsService.fitMapToProject(mapWrapper, this.project, -150);
            mapWrapper.map.invalidateSize();
        });
    }

    toggleSelected(event) {
        this.onSelect({project: this.project, selected: !this.selectedStatus});
        event.stopPropagation();
    }

    getProjectStatus() {
        if (!this.statusFetched) {
            this.projectService.getProjectStatus(this.project.id).then(status => {
                this.status = status;
            });
            this.statusFetched = true;
        }
    }

    publishModal() {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        this.activeModal = this.$uibModal.open({
            component: 'rfProjectPublishModal',
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
                this.projectService.deleteProject(this.project.id).then(
                    () => {
                        this.$state.reload();
                    },
                    (err) => {
                        this.$log.debug('error deleting project', err);
                    }
                );
            });
    }
}
