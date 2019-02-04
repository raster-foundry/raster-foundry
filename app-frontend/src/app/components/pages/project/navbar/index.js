import _ from 'lodash';
import tpl from './index.html';
import {Set} from 'immutable';

class ProjectLayersNavController {
    constructor(
        $rootScope, $state, $log,
        projectService, paginationService, modalService, authService, mapService
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        this.navs = [];
        const stateName = this.$state.current.name;
        this.$log.log(this.$state.current);
        if (
            stateName === 'project.layers' ||
            stateName === 'project.analyses' ||
            stateName === 'project.settings.options'
        ) {
            this.navs.push({
                title: this.project.name,
                sref: `project.layers({projectId: '${this.project.id}'})`,
                click: true
            });
        }
    }

    $onDestroy() {
    }
}

const component = {
    bindings: {
        user: '<',
        userRoles: '<',
        project: '<?',
        layerId: '<?'
    },
    templateUrl: tpl,
    controller: ProjectLayersNavController.name
};

export default angular
    .module('components.pages.projects.navbar', [])
    .controller(ProjectLayersNavController.name, ProjectLayersNavController)
    .component('rfProjectLayersNav', component)
    .name;
