import angular from 'angular';
import _ from 'lodash';
import addUserModalTpl from './addUserModal.html';
import {Map} from 'immutable';

const AddUserModalComponent = {
    templateUrl: addUserModalTpl,
    controller: 'AddUserModalController',
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    }
};

class AddUserModalController {
    constructor(
        $log, $q, $scope,
        authService, platformService, organizationService, teamService, userService
    ) {
        'ngInject';
        this.$log = $log;
        this.$q = $q;
        this.$scope = $scope;
        this.authService = authService;
        this.platformService = platformService;
        this.organizationService = organizationService;
        this.teamService = teamService;
        this.userService = userService;

        this.platformId = this.resolve.platformId;
        this.organizationId = this.resolve.organizationId;
        this.selected = new Map();
    }

    $onInit() {
        let debouncedSearch = _.debounce(
            this.onSearch.bind(this),
            500,
            {leading: false, trailing: true}
        );

        this.$scope.$watch('$ctrl.search', debouncedSearch);

        this.isPlatformAdmin = this.authService.isEffectiveAdmin(this.resolve.platformId);
    }

    onSearch(search) {
        this.fetchUsers(1, search);
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
        if (this.resolve.groupType === 'organization') {
            if (this.isPlatformAdmin) {
                this.fetchPlatformUsers(page, search);
            } else if (search && search.length) {
                this.userService.searchUsers(search).then(response => {
                    this.hasPermission = true;
                    this.fetching = false;
                    this.users = response;
                });
            } else {
                this.$scope.$evalAsync(() => {
                    this.hasPermission = true;
                    this.fetching = false;
                    this.users = [];
                });
            }
        } else if (this.resolve.groupType === 'team') {
            if (this.isPlatformAdmin) {
                this.fetchPlatformUsers(page, search);
            } else {
                this.organizationService.getMembers(
                    this.platformId,
                    this.organizationId,
                    page - 1,
                    search && search.length ? search : null
                ).then((response) => {
                    this.hasPermission = true;
                    this.fetching = false;
                    this.updatePagination(response);
                    this.lastUserResult = response;
                    this.users = response.results;
                }, (error) => {
                    this.permissionDenied(error, 'organization admin');
                });
            }
        }
    }

    fetchPlatformUsers(page, search) {
        this.platformService.getMembers(
            this.platformId,
            page - 1,
            search && search.length ? search : null
        ).then((response) => {
            this.hasPermission = true;
            this.fetching = false;
            this.updatePagination(response);
            this.lastUserResult = response;
            this.users = response.results;
        }, (error) => {
            // platform admins or super users are allowed to list platform users
            this.permissionDenied(error, 'platform admin');
        });
    }

    toggleUserSelect(user, adminToggle) {
        if (this.selected.has(user.id) && !adminToggle) {
            this.selected = this.selected.delete(user.id);
        } else if (this.selected.has(user.id)) {
            this.selected = this.selected.set(user.id, !this.selected.get(user.id));
        } else {
            this.selected = this.selected.set(user.id, adminToggle);
        }
    }

    addUsers() {
        delete this.error;
        let promises = this.selected.entrySeq().toArray().map((select) => {
            const userId = select[0];
            const isAdmin = select[1];
            if (this.resolve.groupType === 'team') {
                return this.teamService.addUserWithRole(
                    this.resolve.platformId,
                    this.resolve.organizationId,
                    this.resolve.teamId,
                    isAdmin ? 'ADMIN' : 'MEMBER',
                    userId
                );
            } else if (this.resolve.groupType === 'organization') {
                return this.organizationService.addUserWithRole(
                    this.resolve.platformId,
                    this.resolve.organizationId,
                    isAdmin ? 'ADMIN' : 'MEMBER',
                    userId
                );
            }
            throw new Error('Undefined admin view - cannot add users without a defined group');
        });
        this.$q.all(promises).then(() => {
            this.close();
        }, (err) => {
            // eslint-disable-next-line
            this.error = err.data;
            this.$log.error('Error adding users to team:', err);
        });
    }

    permissionDenied(err, subject, adminEmail) {
        this.fetching = false;
        this.hasPermission = false;
        this.subject = subject;
        this.adminEmail = adminEmail;
        this.permissionDeniedMsg = `${err.data}. Please contact `;
    }
}


const AddUserModalModule = angular.module('components.settings.addUserModal', []);

AddUserModalModule.component('rfAddUserModal', AddUserModalComponent);
AddUserModalModule.controller('AddUserModalController', AddUserModalController);

export default AddUserModalModule;
