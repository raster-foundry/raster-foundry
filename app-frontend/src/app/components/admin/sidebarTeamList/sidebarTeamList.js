import tpl from './sidebarTeamList.html';

const SidebarTeamListComponent = {
    bindings: {
        paginatedResponse: '<',
        sref: '@'
    },
    templateUrl: tpl
};

export default angular
    .module('components.sidebarTeamList', [])
    .component('rfSidebarTeamList', SidebarTeamListComponent);
