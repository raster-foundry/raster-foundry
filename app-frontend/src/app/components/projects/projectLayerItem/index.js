import angular from 'angular';
import { get } from 'lodash';

import projectLayerPlaceholder from '../../../../assets/images/transparent.svg';
import projectLayerItemTpl from './index.html';

class ProjectLayerItemController {
    constructor(
        $rootScope,
        $scope,
        projectService,
        mapService,
        mapUtilsService,
        userService
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
        this.projectLayerPlaceholder = projectLayerPlaceholder;
    }

    $onInit() {
        this.$scope.$watch(
            () => this.selected({ project: this.item }),
            selected => {
                this.selectedStatus = selected;
            }
        );
        this.mapId = `${this.item.id}-map`;

        this.ownerAvatarUrl = '';
        this.getOwnerAvatarUrl();
        this.getProjectLayerStatus();
    }

    getMap() {
        return this.mapService.getMap(this.mapId);
    }

    addProjectLayerTile() {
        const layer = this.projectService.mapLayerFromLayer(this.project, this.item);
        this.getMap().then(m => {
            m.addLayer('share-layer', layer);
        });
    }

    fitProjectLayerExtent() {
        this.getMap().then(mapWrapper => {
            this.mapUtilsService.fitMapToProjectLayer(
                mapWrapper,
                this.item,
                this.project,
                -2
            );
            mapWrapper.map.invalidateSize();
        });
    }

    toggleSelected(event) {
        this.onSelect({ project: this.item, selected: !this.selectedStatus });
        event.stopPropagation();
    }

    getProjectLayerStatus() {
        if (!this.statusFetched) {
            this.projectService
                .getProjectLayerStatus(this.project.id, this.item.id)
                .then(status => {
                    this.status = status;
                    if (this.status === 'CURRENT') {
                        this.fitProjectLayerExtent();
                        this.addProjectLayerTile();
                        this.mapOptions = { attributionControl: false };
                    }
                });
            this.statusFetched = true;
        }
    }

    getOwnerAvatarUrl() {
        const owner = get(this, 'item.owner') || get(this, 'project.owner');
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

const ProjectLayerItemComponent = {
    templateUrl: projectLayerItemTpl,
    controller: 'ProjectLayerItemController',
    bindings: {
        item: '<',
        selected: '&',
        onSelect: '&',
        project: '<'
    }
};
export default angular
    .module('components.projects.projectLayerItem', [])
    .controller('ProjectLayerItemController', ProjectLayerItemController)
    .component('rfProjectLayerItem', ProjectLayerItemComponent)
    .name;
