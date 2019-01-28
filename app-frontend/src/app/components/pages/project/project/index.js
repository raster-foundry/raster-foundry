import {Map} from 'immutable';
import tpl from './index.html';

class ProjectPageController {
    constructor(
        $rootScope, $state, $location, mapService, mapUtilsService,
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $postLink() {
        if (!this.$location.search().bbox) {
            this.fitProjectExtent();
        }
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
    .component('rfProjectPage', component)
    .name;
