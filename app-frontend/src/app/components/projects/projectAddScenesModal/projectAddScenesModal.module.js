import angular from 'angular';
import _ from 'lodash';
import projectAddScenesModalTpl from './projectAddScenesModal.html';

const ProjectAddScenesModalComponent = {
    templateUrl: projectAddScenesModalTpl,
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    },
    controller: 'ProjectAddScenesModalController'
};

class ProjectAddScenesModalController {
    constructor($log, $state, $q, projectService, modalService) {
        'ngInject';
        this.$log = $log;
        this.$state = $state;
        this.$q = $q;
        this.projectService = projectService;
        this.modalService = modalService;
        this.scenes = this.resolve.scenes;
        this.selectedScenes = this.scenes;
        this.project = this.resolve.project;
    }

    isSelected(scene) {
        return this.selectedScenes.has(scene.id);
    }

    viewSceneDetail(selection) {
        this.modalService.open({
            component: 'rfSceneDetailModal',
            resolve: {
                scene: () => selection.scene,
                repository: () => selection.repository
            }
        });
    }

    addScenesToProject() {
        this.sceneIds = Array.from(this.selectedScenes.keys());
        let repositoryScenes = _.groupBy(
            this.selectedScenes.valueSeq().toArray(),
            ({repository}) => {
                return repository.label;
            }
        );
        let repositories = _.map(repositoryScenes, (scenes, label) => {
            return {label, repository: _.first(scenes).repository};
        });

        let projectRequests = repositories.map(({label, repository}) => {
            let scenes = repositoryScenes[label].map(({scene}) => scene);
            return repository
                .service
                .addToProject(this.resolve.project.id, scenes)
                .then(() => ({repository: label, sceneCount: scenes.length}));
        });

        this.$q.all(projectRequests).then((repositoryCounts) => {
            this.finished = true;
            this.repositoryCounts = repositoryCounts;
        }, (err) => {
            this.$log.error(
                'Error while adding scenes to projects',
                err
            );
        });
    }
}

const ProjectAddScenesModalModule = angular.module('components.projects.projectAddScenesModal', []);

ProjectAddScenesModalModule.controller(
    'ProjectAddScenesModalController', ProjectAddScenesModalController
);
ProjectAddScenesModalModule.component(
    'rfProjectAddScenesModal', ProjectAddScenesModalComponent
);

export default ProjectAddScenesModalModule;
