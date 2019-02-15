import tpl from './index.html';
class SelectedActionsBar {
}

const component = {
    bindings: {
        checked: '<',
        onClick: '&',
        actionText: '<'
    },
    templateUrl: tpl,
    controller: SelectedActionsBar.name,
    transclude: true
};

export default angular
    .module('components.projects.selectedActionsBar', [])
    .controller(SelectedActionsBar.name, SelectedActionsBar)
    .component('rfSelectedActionsBar', component)
    .name;
