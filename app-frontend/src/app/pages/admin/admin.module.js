import angular from 'angular';

class AdminController {
    constructor(
        $scope,
        authService, platformService, organizationService
    ) {
        'ngInject';

        this.authService = authService;
        this.platformService = platformService;
        this.organizationService = organizationService;
        this.$scope = $scope;
        this.platforms = [];
        this.organizations = [];

        this.$scope.$watch('$ctrl.authService.userRoles', (roles) => {
            if (roles && roles.length) {
                this.platforms = [];
                this.organizations = [];
                roles.filter((role) => role.groupType === 'PLATFORM').forEach((role) => {
                    this.platformService.getPlatform(role.groupId).then((item) => {
                        this.platforms.push({role, item});
                    });
                });
                roles.filter((role) => role.groupType === 'ORGANIZATION').forEach((role) => {
                    this.organizationService.getOrganization(role.groupId).then((item) => {
                        this.organizations.push({role, item});
                    });
                });
            }
        });
    }
}

const AdminModule = angular.module('pages.admin', []);
AdminModule.controller('AdminController', AdminController);

export default AdminModule;
