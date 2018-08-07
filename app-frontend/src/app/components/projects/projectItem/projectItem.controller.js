import projectPlaceholder from '../../../../assets/images/transparent.svg';

export default class ProjectItemController {
    constructor(
        $rootScope, $scope, $state, $attrs, $log,
        projectService, mapService, mapUtilsService, authService, modalService,
        featureFlags
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
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
        this.mapId = `${this.project.id}-map`;
        this.getMap = () => this.mapService.getMap(this.mapId);

        this.showProjectThumbnail =
            !this.featureFlags.isOnByDefault('project-preview-mini-map');

        this.getProjectStatus();
    }

    getThumbnailURL() {
        this.thumbnailUrl = this.projectService.getProjectThumbnailURL(
            this.project, this.authService.token()
        );
    }

    addProjectLayer() {
        let url = this.projectService.getProjectLayerURL(
            this.project,
            {token: this.authService.token()}
        );

        let layer = L.tileLayer(url);

        this.getMap().then(m => {
            m.addLayer('share-layer', layer);
        });
    }

    fitProjectExtent() {
        this.getMap().then(mapWrapper => {
            this.mapUtilsService.fitMapToProject(mapWrapper, this.project, -2);
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
                if (this.status === 'CURRENT') {
                    this.fitProjectExtent();
                    this.addProjectLayer();

                    if (this.showProjectThumbnail) {
                        this.getThumbnailURL();
                    } else {
                        this.mapOptions = {attributionControl: false};
                    }
                }
            });
            this.statusFetched = true;
        }
    }

    publishModal() {
        this.modalService.open({
            component: 'rfProjectPublishModal',
            resolve: {
                project: () => this.project,
                tileUrl: () => this.projectService.getProjectLayerURL(this.project),
                shareUrl: () => this.projectService.getProjectShareURL(this.project)
            }
        });
    }

    shareModal(project) {
        this.modalService.open({
            component: 'rfPermissionModal',
            size: 'med',
            resolve: {
                object: () => project,
                permissionsBase: () => 'projects',
                objectType: () => 'PROJECT',
                objectName: () => project.name,
                platform: () => this.platform
            }
        });
    }

    deleteModal() {
        const modal = this.modalService.open({
            component: 'rfConfirmationModal',
            resolve: {
                title: () => 'Delete Project?',
                subtitle: () =>
                    'The project will be permanently deleted,'
                    + ' but scenes will be unaffected.',
                content: () =>
                    '<div class="color-danger">'
                    + 'You are about to delete the project. '
                    + 'Annotations, exports, and analyses that use this project '
                    + 'will not longer be accessible. This action is not reversible. '
                    + 'Are you sure you wish to continue?'
                    + '</div>',
                confirmText: () => 'Delete Project',
                cancelText: () => 'Cancel'
            }
        });

        modal.result.then(() => {
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
