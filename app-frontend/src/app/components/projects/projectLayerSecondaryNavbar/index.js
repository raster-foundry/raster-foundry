import tpl from './index.html';

class ProjectLayerSecondaryNavbarController {
}

const component = {
    bindings: {
    },
    templateUrl: tpl,
    controller: ProjectLayerSecondaryNavbarController.name
};

export default angular
    .module('components.projects.projectLayerSecondaryNavbar', [])
    .controller(ProjectLayerSecondaryNavbarController.name, ProjectLayerSecondaryNavbarController)
    .component('rfProjectLayerSecondaryNavbar', component)
    .name;
