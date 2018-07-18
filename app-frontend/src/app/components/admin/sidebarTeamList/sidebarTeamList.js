import tpl from './sidebarTeamList.html';

const SidebarTeamListComponent = {
    controller: 'SidebarTeamListController',
    bindings: {
        paginatedResponse: '<',
        showOrgLogo: '<?',
        displayLimit: '<?',
        sref: '@'
    },
    templateUrl: tpl
};

const defaultDisplayLimit = 3;

class SidebarTeamListController {
    constructor(organizationService) {
        'ngInject';
        this.organizationService = organizationService;
        this.displayLimit = this.displayLimit || defaultDisplayLimit;
    }

    $onInit() {
        this.displayTeams = this.paginatedResponse.results.length <= this.displayLimit ?
            this.paginatedResponse.results :
            this.paginatedResponse.results.slice(this.displayLimit - 1);
        if (this.showOrgLogo) {
            this.getOrganizations();
        }
    }

    getOrganizations() {
        this.orgURIs = this.displayTeams.map(team => team.organizationId)
            .filter((val, idx, self) => self.indexOf(val) === idx)
            .reduce((obj, orgId) => {
                obj[orgId] = '';
                return obj;
            }, {});

        Object.keys(this.orgURIs).forEach(orgId => {
            this.organizationService.getOrganization(orgId).then(resp => {
                this.orgURIs[orgId] = this.cacheBustUri(resp.logoUri);
            });
        });
    }

    cacheBustUri(uri) {
        return `${uri}?${new Date().getTime()}`;
    }
  }

const SidebarTeamListModule = angular.module('components.sidebarTeamList', []);

SidebarTeamListModule.component('rfSidebarTeamList', SidebarTeamListComponent);
SidebarTeamListModule.controller('SidebarTeamListController', SidebarTeamListController);

export default SidebarTeamListModule;
