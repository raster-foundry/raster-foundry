export default class AOIApproveController {
    constructor($scope, $state, $q, $log, projectService, mapService) {
        'ngInject';
        this.$parent = $scope.$parent.$ctrl;
        this.$state = $state;
        this.$q = $q;
        this.$log = $log;
        this.projectService = projectService;
        this.mapService = mapService;
        this.getMap = () => this.mapService.getMap('edit');
    }

    $onInit() {
        this.pendingSceneList = [];
        this.$parent.getPendingSceneList().then(pendingScenes => {
            this.pendingSceneList = pendingScenes;
            this.resetAllScenes();
            this.sceneStatusCounts = this.getSceneStatusCount();
        });
    }

    isSceneApproved(scene) {
        return this.sceneStatuses[scene.id] === 'APPROVED';
    }

    isSceneRejected(scene) {
        return this.sceneStatuses[scene.id] === 'REJECTED';
    }

    resetScene(scene) {
        this.sceneStatuses[scene.id] = null;
        this.sceneStatusCounts = this.getSceneStatusCount();
    }

    toggleSceneApproval(scene) {
        this.sceneStatuses[scene.id] = this.isSceneApproved(scene) ? null : 'APPROVED';
        this.sceneStatusCounts = this.getSceneStatusCount();
    }

    toggleSceneRejection(scene) {
        this.sceneStatuses[scene.id] = this.isSceneRejected(scene) ? null : 'REJECTED';
        this.sceneStatusCounts = this.getSceneStatusCount();
    }

    resetAllScenes() {
        this.sceneStatuses = this.pendingSceneList.reduce((statuses, scene) => {
            statuses[scene.id] = null;
            return statuses;
        }, {});
        this.sceneStatusCounts = this.getSceneStatusCount();
    }

    approveAllScenes() {
        this.sceneStatuses = this.pendingSceneList.reduce((statuses, scene) => {
            statuses[scene.id] = 'APPROVED';
            return statuses;
        }, {});
        this.sceneStatusCounts = this.getSceneStatusCount();
    }

    rejectAllScenes() {
        this.sceneStatuses = this.pendingSceneList.reduce((statuses, scene) => {
            statuses[scene.id] = 'REJECTED';
            return statuses;
        }, {});
        this.sceneStatusCounts = this.getSceneStatusCount();
    }

    toggleAllSceneApproval() {
        if (this.sceneStatusCounts.APPROVED === this.sceneStatusCounts.TOTAL) {
            this.resetAllScenes();
        } else {
            this.approveAllScenes();
        }
    }

    setHoveredScene(scene) {
        if (scene !== this.hoveredScene) {
            this.hoveredScene = scene;
            this.getMap().then((map) => {
                map.setThumbnail(scene);
            });
        }
    }

    removeHoveredScene() {
        this.getMap().then((map) => {
            delete this.hoveredScene;
            map.deleteThumbnail();
        });
    }

    getSceneStatusCount() {
        return Object.keys(this.sceneStatuses).reduce((counts, id) => {
            const status = this.sceneStatuses[id];
            if (status === 'APPROVED') {
                counts.APPROVED += 1;
            } else if (status === 'REJECTED') {
                counts.REJECTED += 1;
            }
            counts.TOTAL += 1;
            return counts;
        }, {
            APPROVED: 0,
            REJECTED: 0,
            TOTAL: 0
        });
    }

    getScenesStatusIds() {
        return Object.keys(this.sceneStatuses).reduce((statuses, id) => {
            const status = this.sceneStatuses[id];
            if (status === 'APPROVED') {
                statuses.APPROVED.push(id);
            } else if (status === 'REJECTED') {
                statuses.REJECTED.push(id);
            }
            return statuses;
        }, {
            APPROVED: [],
            REJECTED: []
        });
    }

    applySceneStatuses() {
        this.$parent.fetchProject().then(project => {
            const scenesToHandle = this.getScenesStatusIds();
            const requests = [];
            requests.push(
                this.projectService.removeScenesFromProject(project.id, scenesToHandle.REJECTED)
            );
            // @TODO: refactor this when we have a bulk approval endpoint
            scenesToHandle.APPROVED.forEach(id => {
                requests.push(
                    this.projectService.approveScene(project.id, id)
                );
            });
            this.$q.all(requests).then(() => {
                this.$state.go('projects.edit', {}, { reload: true });
            }, () => {
                this.$log.error('There was a problem applying the status to one or more scenes');
            });
        });
    }
}
