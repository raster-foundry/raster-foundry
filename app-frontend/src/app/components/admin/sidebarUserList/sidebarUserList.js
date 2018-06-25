import tpl from './sidebarUserList.html';

const SidebarUserListComponent = {
    bindings: {
        paginatedResponse: '<',
        sref: '@'
    },
    templateUrl: tpl
};

export default angular
    .module('components.sidebarUserList', [])
    .component('rfSidebarUserList', SidebarUserListComponent);
