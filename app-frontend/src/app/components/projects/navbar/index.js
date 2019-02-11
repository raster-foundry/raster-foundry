import _ from 'lodash';
import tpl from './index.html';
import {Set} from 'immutable';

class ProjectLayersNavController {
    constructor(
        $rootScope, $state, $scope, $transitions
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        this.navs = [];
        this.onStateCurrentChange(this.$state.current);
        this.$transitions.onSuccess({}, transition => {
            const toState = transition.to();
            this.onStateCurrentChange(toState);
        });
    }

    onStateCurrentChange(stateCurrent) {
        this.navs = [];

        if (
            stateCurrent.name === 'project.layers' ||
            stateCurrent.name.includes('project.analyses') ||
            stateCurrent.name.includes('project.settings') ||
            stateCurrent.name.includes('project.layer')
        ) {
            this.navs.push({
                title: this.project.name,
                sref: `project.layers({projectId: '${this.project.id}'})`
            });
        }

        if (stateCurrent.name.includes('project.settings')) {
            this.navs.push({
                title: 'Settings',
                sref: `project.settings({projectId: '${this.project.id}'})`
            });
        }

        if (stateCurrent.name === 'project.layer' ||
            stateCurrent.name.includes('project.layer.')
        ) {
            this.navs.push({
                title: this.layer.name,
                sref: `project.layer({
                    projectId: '${this.project.id}',
                    layerId: '${this.layer.id}'
                })`
            });
        }

        if (stateCurrent.name.includes('project.layer.corrections')) {
            this.navs.push({
                title: 'Color correct',
                sref: `project.layer.corrections({
                    projectId: '${this.project.id}',
                    layerId: '${this.layer.id}'
                })`
            });
        }

        if (stateCurrent.name.includes('project.layer.scenes.browse')) {
            this.navs.push({
                title: 'Browse imagery',
                sref: `project.layer.scenes.browse({
                    projectId: '${this.project.id}',
                    layerId: '${this.layer.id}'
                })`
            });
        }
    }
}

const component = {
    bindings: {
        user: '<',
        userRoles: '<',
        project: '<?',
        layerId: '<?',
        layer: '<?'
    },
    templateUrl: tpl,
    controller: ProjectLayersNavController.name
};

export default angular
    .module('components.pages.projects.navbar', [])
    .controller(ProjectLayersNavController.name, ProjectLayersNavController)
    .component('rfProjectLayersNav', component)
    .name;
