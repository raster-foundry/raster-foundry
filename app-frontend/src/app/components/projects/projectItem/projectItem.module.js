/* global BUILDCONFIG */
import angular from 'angular';
import { get } from 'lodash';

import projectPlaceholder from '../../../../assets/images/transparent.svg';
import projectItemTpl from './projectItem.html';

const ProjectItemComponent = {
    templateUrl: projectItemTpl,
    controller: 'ProjectItemController',
    transclude: true,
    bindings: {
        item: '<',
        selected: '&',
        onSelect: '&',
        slim: '<',
        hideOptions: '<',
        platform: '<',
        user: '<'
    }
};

class ProjectItemController {
    constructor(
        $rootScope,
        $scope,
        $state,
        $attrs,
        $log,
        $q,
        projectService,
        mapService,
        mapUtilsService,
        authService,
        modalService,
        permissionsService,
        userService,
        featureFlags
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
        this.projectPlaceholder = projectPlaceholder;
    }

    $onInit() {
        this.isSelectable = this.$attrs.hasOwnProperty('selectable');
        this.$scope.$watch(
            () => this.selected({ project: this.item }),
            selected => {
                this.selectedStatus = selected;
            }
        );
        this.mapId = `${this.item.id}-map`;

        this.showProjectThumbnail = !this.featureFlags.isOnByDefault('project-preview-mini-map');

        this.ownerAvatarUrl = '';
        this.getOwnerAvatarUrl();
        this.getProjectStatus();

        if (!this.user || this.hideOptions || this.isLayer) {
            this.permissions = [];
        } else {
            this.projectService.getProjectPermissions(this.item, this.user).then(permissions => {
                this.permissions = permissions;
            });
        }
    }

    getMap() {
        return this.mapService.getMap(this.mapId);
    }

    getThumbnailURL() {
        this.thumbnailUrl = this.projectService.getProjectThumbnailURL(
            this.item,
            this.authService.token()
        );
    }

    addProjectTile() {
        const layer = L.tileLayer(
            this.projectService.getProjectTileURL(this.item, {
                token: this.authService.token()
            }),
            {maxNativeZoom: BUILDCONFIG.TILES_MAX_ZOOM}
        );

        this.getMap().then(m => {
            m.addLayer('share-layer', layer);
        });
    }

    fitProjectExtent() {
        this.getMap().then(mapWrapper => {
            this.mapUtilsService.fitMapToProject(mapWrapper, this.item, -2);
            mapWrapper.map.invalidateSize();
        });
    }

    toggleSelected(event) {
        this.onSelect({ project: this.item, selected: !this.selectedStatus });
        event.stopPropagation();
    }

    getProjectStatus() {
        if (!this.statusFetched) {
            this.projectService.getProjectStatus(this.item.id).then(status => {
                this.status = status;
                if (this.status === 'CURRENT') {
                    this.fitProjectExtent();
                    this.addProjectTile();
                    if (this.showProjectThumbnail) {
                        this.getThumbnailURL();
                    } else {
                        this.mapOptions = { attributionControl: false };
                    }
                }
            });
            this.statusFetched = true;
        }
    }

    publishModal() {
        this.modalService
            .open({
                component: 'rfProjectPublishModal',
                resolve: {
                    project: () => this.item,
                    tileUrl: () => this.projectService.getProjectTileURL(this.item),
                    shareUrl: () => this.projectService.getProjectShareURL(this.item)
                }
            })
            .result.catch(() => {});
    }

    shareModal(project) {
        this.modalService
            .open({
                component: 'rfPermissionModal',
                resolve: {
                    object: () => project,
                    permissionsBase: () => 'projects',
                    objectType: () => 'PROJECT',
                    objectName: () => project.name,
                    platform: () => this.platform
                }
            })
            .result.catch(() => {});
    }

    deleteModal() {
        const modal = this.modalService.open({
            component: 'rfFeedbackModal',
            resolve: {
                title: () => 'Project deletion',
                subtitle: () =>
                    'The project will be deleted, ' + 'but scenes will remain unaffected.',
                content: () =>
                    '<h2>Do you wish to continue?</h2>' +
                    '<p>Deleting the project will also make ' +
                    'associated annotations, exports and ' +
                    'analyses inaccessible. This is a ' +
                    'permanent action.</p>',
                /* feedbackIconType : default, success, danger, warning */
                feedbackIconType: () => 'danger',
                feedbackIcon: () => 'icon-warning',
                feedbackBtnType: () => 'btn-danger',
                feedbackBtnText: () => 'Delete project',
                cancelText: () => 'Cancel'
            }
        });

        modal.result
            .then(() => {
                this.projectService.deleteProject(this.item.id).then(
                    () => {
                        this.$state.reload();
                    },
                    err => {
                        this.$log.debug('error deleting project', err);
                    }
                );
            })
            .catch(() => {});
    }

    getOwnerAvatarUrl() {
        const owner = get(this, 'item.owner') || get(this, 'parentProject.owner');
        this.ownerAvatarUrl = owner.profileImageUri;
        if (!this.ownerAvatarUrl && typeof owner === 'string') {
            this.userService.getUserById(owner).then(user => {
                // Public user objects contain profile image
                // so getting it from a shared project's owner should work
                this.ownerAvatarUrl = user.profileImageUri;
            });
        }
    }
}

const ProjectItemModule = angular.module('components.projects.projectItem', []);

ProjectItemModule.controller('ProjectItemController', ProjectItemController);
ProjectItemModule.component('rfProjectItem', ProjectItemComponent);

export default ProjectItemModule;
