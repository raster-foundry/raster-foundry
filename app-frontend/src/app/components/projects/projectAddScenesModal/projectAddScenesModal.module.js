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
    constructor($rootScope, $log, $state, $q, projectService, modalService) {
        'ngInject';
        $rootScope.autoInject(this, arguments);

        this.scenes = this.resolve.scenes;
        this.selectedScenes = this.scenes;
        this.project = this.resolve.project;
    }

    $onInit() {
        this.addSceneMsg = '';
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
        this.getSceneImportMsg(repositoryScenes);
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

    getSceneImportMsg(repositoryScenes) {
        let rfScenes = repositoryScenes['Raster Foundry'];
        let nonRfScenes = _.omit(repositoryScenes, ['Raster Foundry']);

        let uningestedNonCogCount = rfScenes && rfScenes.length ?
        rfScenes.filter(rfScene => {
            return rfScene.scene.sceneType !== 'COG' &&
                   rfScene.scene.statusFields.ingestStatus !== 'INGESTED';
        }).length : 0;

        this.addSceneMsg = uningestedNonCogCount ?
            `There are ${uningestedNonCogCount} non-COG Raster Foundry scenes being ingested.` :
            this.addSceneMsg;

        let nonRfScenesMsg = !_.isEmpty(nonRfScenes) ? _.map(nonRfScenes, (vals, key) => {
            return `${vals.length} ${key} scenes`;
        }).join(', ') + ' being imported' : '';

        this.addSceneMsg += nonRfScenesMsg.length ? `There are ${nonRfScenesMsg}.` : '';
    }

    linkToStatus() {
        this.$state.go('projects.edit.scenes', {projectid: this.project.id});
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
