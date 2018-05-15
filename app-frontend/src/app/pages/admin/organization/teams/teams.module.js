import angular from 'angular';
import _ from 'lodash';

class OrganizationTeamsController {
    constructor(
        $scope, $stateParams,
        modalService, organizationService, teamService
    ) {
        this.$scope = $scope;
        this.$stateParams = $stateParams;
        this.modalService = modalService;
        this.organizationService = organizationService;
        this.teamService = teamService;

        let debouncedSearch = _.debounce(
            this.onSearch.bind(this),
            500,
            {leading: false, trailing: true}
        );
        this.fetching = true;
        this.orgWatch = this.$scope.$parent.$watch('$ctrl.organization', (organization) => {
            if (organization && this.orgWatch) {
                this.orgWatch();
                delete this.orgWatch;
                this.organization = organization;
                this.$scope.$watch('$ctrl.search', debouncedSearch);
            }
        });
    }

    onSearch(search) {
        this.fetchTeams(1, search);
    }

    updatePagination(data) {
        this.pagination = {
            show: data.count > data.pageSize,
            count: data.count,
            currentPage: data.page + 1,
            startingItem: data.page * data.pageSize + 1,
            endingItem: Math.min((data.page + 1) * data.pageSize, data.count),
            hasNext: data.hasNext,
            hasPrevious: data.hasPrevious
        };
    }


    fetchTeams(page = 1, search) {
        const platformId = this.organization.platformId;
        const organizationId = this.organization.id;
        this.fetching = true;
        this.organizationService
            .getTeams(platformId, organizationId, page - 1, search)
            .then((response) => {
                this.fetching = false;
                this.updatePagination(response);
                this.lastTeamResult = response;
                this.teams = response.results;

                this.teams.forEach(
                    (team) => Object.assign(
                        team, {
                            options: {
                                items: this.itemsForTeam(team)
                            }
                        }
                    ));

                // fetch team users
                this.teams.forEach(
                    (team) => {
                        this.teamService
                            .getMembers(platformId, organizationId, team.id)
                            .then((paginatedUsers) => {
                                team.fetchedUsers = paginatedUsers;
                            });
                    }
                );
            });
    }


    itemsForTeam(team) {
        /* eslint-disable */
        return [
            {
                label: 'Edit',
                callback: () => {
                    console.log('edit callback for team:', team);
                },
                classes: []
            },
            {
                label: 'Add to team...',
                callback: () => {
                    console.log('Add to team... callback for team:', team);
                },
                classes: []
            },
            {
                label: 'Delete',
                callback: () => {
                    console.log('delete callback for team:', team);
                },
                classes: ['color-danger']
            }
        ];
        /* eslint-enable */
    }

    newTeamModal() {
        this.modalService.open({
            component: 'rfTeamModal',
            resolve: { },
            size: 'sm'
        }).result.then((result) => {
            // eslint-disable-next-line
            this.teamService.createTeam(this.organization.platformId, this.organization.id, result.name).then(() => {
                this.fetchTeams(this.pagination.currentPage, this.search);
            });
        });
    }
}

const OrganizationTeamsModule = angular.module('pages.organization.teams', []);
OrganizationTeamsModule.controller('OrganizationTeamsController', OrganizationTeamsController);

export default OrganizationTeamsModule;
