import _ from 'lodash';
import {Set} from 'immutable';

export default class AOIApproveController {
    constructor(
        $scope, $state, $q, $log, $window, $stateParams,
        projectService, projectEditService, mapService,
        paginationService,
        RasterFoundryRepository
    ) {
        'ngInject';
        $scope.autoInject(this, arguments);
        this.$parent = $scope.$parent.$ctrl;
        this.repository = {
            name: 'Raster Foundry',
            service: RasterFoundryRepository
        };
        this.getMap = () => this.mapService.getMap('edit');
    }

    $onInit() {
        this.resetAllScenes();
        this.resetStatusSets();
    }

    fetchPage(page = this.$state.params.page || 1) {
        delete this.fetchError;
        this.pendingSceneList = [];
        const currentQuery = this.projectService.getProjectScenes(
            this.$parent.projectId, {
                pending: true,
                pageSize: 30,
                page: page - 1
            }
        ).then((paginatedResponse) => {
            this.pendingSceneList = paginatedResponse.results;
            this.pagination = this.paginationService.buildPagination(paginatedResponse);
            this.paginationService.updatePageParam(page);
            if (this.currentQuery === currentQuery) {
                delete this.fetchError;
            }
        }, (e) => {
            if (this.currentQuery === currentQuery) {
                this.fetchError = e;
            }
        }).finally(() => {
            if (this.currentQuery === currentQuery) {
                delete this.currentQuery;
            }
        });
        this.currentQuery = currentQuery;
        return currentQuery;
    }

    resetStatusSets() {
        this.approvedSet = new Set();
        this.rejectedSet = new Set();
    }

    isSceneApproved(scene) {
        return this.approvedSet.has(scene.id);
    }

    isSceneRejected(scene) {
        return this.rejectedSet.has(scene.id);
    }

    resetScene(scene) {
        this.approvedSet = this.approvedSet.delete(scene.id);
        this.rejectedSet = this.rejectedSet.delete(scene.id);
    }

    toggleSceneApproval(scene) {
        this.rejectedSet = this.rejectedSet.delete(scene.id);
        this.approvedSet = this.approvedSet.add(scene.id);
    }

    toggleSceneRejection(scene) {
        this.approvedSet = this.approvedSet.delete(scene.id);
        this.rejectedSet = this.rejectedSet.add(scene.id);
    }

    resetAllScenes() {
        this.fetchPage(1);
        this.resetStatusSets();
    }

    currentPageApproved() {
        const currentSceneSet = new Set(this.pendingSceneList.map(s => s.id));
        return currentSceneSet.size === this.approvedSet.intersect(currentSceneSet).size;
    }

    toggleAllSceneApproval() {
        const currentSceneSet = new Set(this.pendingSceneList.map(s => s.id));
        if (currentSceneSet.size === this.approvedSet.intersect(currentSceneSet).size) {
            this.approvedSet = this.approvedSet.subtract(currentSceneSet);
        } else {
            this.approvedSet = this.approvedSet.union(currentSceneSet);
            this.rejectedSet = this.rejectedSet.subtract(currentSceneSet);
        }
    }

    setHoveredScene(scene) {
        if (scene !== this.hoveredScene) {
            this.hoveredScene = scene;
            this.getMap().then((map) => {
                map.setThumbnail(scene, this.repository);
            });
        }
    }

    removeHoveredScene() {
        this.getMap().then((map) => {
            delete this.hoveredScene;
            map.deleteThumbnail();
        });
    }

    applySceneStatuses() {
        const requests = [];

        if (this.rejectedSet.size) {
            requests.push(this.projectService.removeScenesFromProject(
                this.$stateParams.projectid,
                this.rejectedSet.toArray()
            ));
        }

        if (this.approvedSet.size) {
            requests.push(
              this.projectService.approveScenes(
                  this.$stateParams.projectid, this.approvedSet.toArray()
              ));
        }

        if (requests.length) {
            this.$q.all(requests).then(() => {
                this.$state.go('projects.edit', {page: 1});
            }, () => {
                this.$log.error(
              'There was a problem applying the status to one or more scenes');
            });
        }
    }
}
