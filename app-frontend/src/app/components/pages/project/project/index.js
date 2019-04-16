import { Map } from 'immutable';
import tpl from './index.html';

class ProjectPageController {
    constructor(
        $rootScope,
        $state,
        $location,
        $transitions,
        mapService,
        mapUtilsService,
        projectService
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $postLink() {
        if (!this.$location.search().bbox) {
            this.fitProjectExtent();
        }
        this.getMap().then(map => {
            this.$transitions.onFinish(
                {
                    to: 'project.*'
                },
                transition => {
                    if (!transition.to().name.includes('create-analysis')) {
                        map.map.invalidateSize();
                    }
                }
            );
        });
    }

    $onDestroy() {
        this.projectService.setVisibleProjectLayers();
    }

    getMap() {
        return this.mapService.getMap('project');
    }

    fitProjectExtent() {
        this.getMap().then(m => {
            this.mapUtilsService.fitMapToProject(m, this.project);
        });
    }
}

const component = {
    bindings: {
        projectId: '<',
        project: '<'
    },
    templateUrl: tpl,
    controller: ProjectPageController.name
};

export default angular
    .module('components.pages.project.page', [])
    .controller(ProjectPageController.name, ProjectPageController)
    .component('rfProjectPage', component).name;
