import tpl from './sidebarTeamList.html';

const SidebarTeamListComponent = {
    controller: 'SidebarTeamListController',
    bindings: {
        paginatedResponse: '<',
        showOrgLogo: '<?',
        sref: '@'
    },
    templateUrl: tpl
};

const displayLimit = 3;

class SidebarTeamListController {
    constructor(organizationService) {
        'ngInject';
        this.organizationService = organizationService;
        this.displayLimit = displayLimit;
    }

    $onInit() {
        if (this.showOrgLogo) {
            this.diplayTeams = this.paginatedResponse.results.length <= this.displayLimit ?
                this.paginatedResponse.results : this.paginatedResponse.results.slice(2);
            this.getOrganizations();
        }
    }

    getOrganizations() {
        this.orgURIs = this.diplayTeams.map(team => team.organizationId)
            .filter((val, idx, self) => self.indexOf(val) === idx)
            .reduce((obj, orgId) => {
                obj[orgId] = '';
                return obj;
            }, {});

        Object.keys(this.orgURIs).forEach(orgId => {
            this.organizationService.getOrganization(orgId).then(resp => {
                this.orgURIs[orgId] = resp.logoUri;
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
