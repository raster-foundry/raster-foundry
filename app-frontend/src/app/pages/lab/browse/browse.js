class LabBrowseController {
    constructor($scope, modalService) {
        'ngInject';
        $scope.autoInject(this, arguments);
    }

    openTemplateCreateModal() {
        this.modalService.open({
            component: 'rfTemplateCreateModal'
        });
    }
}

const LabBrowseModule = angular
  .module('pages.lab.browse', [])
  .controller('LabBrowseController', LabBrowseController);

LabBrowseModule.resolve = {
    user: ($stateParams, authService) => {
        return authService.getCurrentUser();
    },
    userRoles: (authService) => {
        return authService.fetchUserRoles();
    },
    platform: (userRoles, platformService) => {
        const platformRole = userRoles.find(r =>
            r.groupType === 'PLATFORM'
        );

        return platformService.getPlatform(platformRole.groupId);
    }
};

export default LabBrowseModule;
