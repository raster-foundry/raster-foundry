import angular from 'angular';

import projectPlaceholder from '../../../../assets/images/transparent.svg';
import projectItemTpl from './projectItem.html';

const ProjectItemComponent = {
    templateUrl: projectItemTpl,
    controller: 'ProjectItemController',
    transclude: true,
    bindings: {
        project: '<',
        selected: '&',
        onSelect: '&',
        slim: '<',
        hideOptions: '<',
        platform: '<'
    }
};

class ProjectItemController {
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

        this.showProjectThumbnail =
            !this.featureFlags.isOnByDefault('project-preview-mini-map');

        this.getProjectStatus();
    }

    getMap() {
        return this.mapService.getMap(this.mapId);
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
        }).result.catch(() => {});
    }

    shareModal(project) {
        this.modalService.open({
            component: 'rfPermissionModal',
            resolve: {
                object: () => project,
                permissionsBase: () => 'projects',
                objectType: () => 'PROJECT',
                objectName: () => project.name,
                platform: () => this.platform
            }
        }).result.catch(() => {});
    }

    deleteModal() {
        const modal = this.modalService.open({
            component: 'rfFeedbackModal',
            resolve: {
                title: () => 'Project deletion',
                subtitle: () =>
                    'The project will be deleted, '
                    + 'but scenes will remain unaffected.',
                content: () =>
                    '<h2>Do you wish to continue?</h2>'
                    + '<p>Deleting the project will also make '
                    + 'associated annotations, exports and '
                    + 'analyses inaccessible. This is a '
                    + 'permanent action.</p>',
                /* feedbackIconType : default, success, danger, warning */
                feedbackIconType: () => 'danger',
                feedbackIcon: () => 'icon-warning',
                feedbackBtnType: () => 'btn-danger',
                feedbackBtnText: () => 'Delete project',
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
        }).catch(() => {});
    }
}

const ProjectItemModule = angular.module('components.projects.projectItem', []);

ProjectItemModule.controller('ProjectItemController', ProjectItemController);
ProjectItemModule.component('rfProjectItem', ProjectItemComponent);

export default ProjectItemModule;
