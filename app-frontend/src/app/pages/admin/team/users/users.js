import angular from 'angular';
import _ from 'lodash';

class TeamUsersController {
    constructor($scope, $stateParams, teamService, modalService) {
        'ngInject';
        this.$scope = $scope;
        this.teamService = teamService;
        this.modalService = modalService;

        this.fetching = true;
    }

    $onInit() {
        this.$scope.$parent.$ctrl.teamPromise.then(({team, organization}) => {
            this.team = team;
            this.organization = organization;
            this.platformId = organization.platformId;

            let debouncedSearch = _.debounce(
                this.onSearch.bind(this),
                500,
                {leading: false, trailing: true}
            );

            this.$scope.$watch('$ctrl.search', debouncedSearch);
        });
    }

    onSearch(search) {
        // eslint-disable-next-line
        this.fetchUsers(undefined, search);
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

    fetchUsers(page = 1, search) {
        this.fetching = true;
        this.teamService.getMembers(
            this.platformId,
            this.organization.id,
            this.team.id,
            page - 1,
            search && search.length ? search : null
        ).then((response) => {
            this.fetching = false;
            this.updatePagination(response);
            this.lastUserResult = response;
            this.users = response.results;

            this.users.forEach(
                (user) => Object.assign(
                    user, {
                        options: {
                            items: this.itemsForUser(user)
                        }
                    }
                ));
        });
    }

    itemsForUser(user) {
        /* eslint-disable */
        return [
            {
                label: 'Edit',
                callback: () => {
                    console.log('edit callback for user:', user);
                }
            },
            {
                label: 'Remove',
                callback: () => {
                    this.removeUser(user);
                }
            }
        ];
        /* eslint-enable */
    }

    addUser() {
        this.modalService.open({
            component: 'rfAddUserModal',
            resolve: {
                platformId: () => this.platformId,
                organizationId: () => this.organization.id,
                teamId: () => this.team.id,
                adminView: () => 'team'
            }
        }).result.then(() => {
            this.fetchUsers(1, this.search);
        });
    }

    removeUser(user) {
        const modal = this.modalService.open({
            component: 'rfConfirmationModal',
            resolve: {
                title: () => `Remove ${user.name} from this team?`,
                confirmText: () => 'Remove User',
                cancelText: () => 'Cancel'
            }
        });

        modal.result.then(() => {
            this.teamService.removeUser(
                this.platformId,
                this.organization.id,
                this.team.id,
                user.id
            ).then(() => {
                this.fetchUsers(this.pagination.currentPage, this.search);
            });
        });
    }
}

const TeamUsersModule = angular.module('pages.admin.team.users', []);
TeamUsersModule.controller('AdminTeamUsersController', TeamUsersController);

export default TeamUsersModule;
