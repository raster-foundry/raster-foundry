import angular from 'angular';

class AdminController {
    constructor(
        $scope,
        authService, platformService, organizationService, teamService
    ) {
        'ngInject';

        this.authService = authService;
        this.platformService = platformService;
        this.organizationService = organizationService;
        this.teamService = teamService;
        this.$scope = $scope;
        this.platforms = [];
        this.organizations = [];
        this.teams = [];
    }

    $onInit() {
        this.$scope.$watch('$ctrl.authService.userRoles', (roles) => {
            if (roles && roles.length && !this.loading) {
                this.loading = true;
                this.platforms = [];
                this.organizations = [];
                this.teams = [];
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
                roles.filter((role) => role.groupType === 'TEAM').forEach((role) => {
                    this.teamService.getTeam(role.groupId).then((item) => {
                        this.teams.push({role, item});
                    });
                });
            }
        });
    }
}

const AdminModule = angular.module('pages.admin', []);
AdminModule.controller('AdminController', AdminController);

export default AdminModule;
