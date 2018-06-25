import tpl from './sidebarOrganizationList.html';

const SidebarOrganizationListComponent = {
    bindings: {
        paginatedResponse: '<',
        sref: '@'
    },
    templateUrl: tpl
};

export default angular
    .module('components.sidebarOrganizationList', [])
    .component('rfSidebarOrganizationList', SidebarOrganizationListComponent);
