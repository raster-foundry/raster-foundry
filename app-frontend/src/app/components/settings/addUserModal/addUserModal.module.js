import angular from 'angular';
import _ from 'lodash';
import addUserModalTpl from './addUserModal.html';
import {Set} from 'immutable';

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
    constructor($log, $q, $scope, platformService, organizationService, teamService) {
        'ngInject';
        this.$log = $log;
        this.$q = $q;
        this.$scope = $scope;
        this.platformService = platformService;
        this.organizationService = organizationService;
        this.teamService = teamService;
        this.platformId = this.resolve.platformId;
        this.organizationId = this.resolve.organizationId;
        this.selected = new Set();
    }

    $onInit() {
        let debouncedSearch = _.debounce(
            this.onSearch.bind(this),
            500,
            {leading: false, trailing: true}
        );

        this.$scope.$watch('$ctrl.search', debouncedSearch);
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
        if (this.resolve.adminView === 'organization') {
            this.platformService.getMembers(
                this.platformId,
                page - 1,
                search && search.length ? search : null
            ).then((response) => {
                this.fetching = false;
                this.updatePagination(response);
                this.lastUserResult = response;
                this.users = response.results;
            });
        } else if (this.resolve.adminView === 'team') {
            this.organizationService.getMembers(
                this.platformId,
                this.organizationId,
                page - 1,
                search && search.length ? search : null
            ).then((response) => {
                this.fetching = false;
                this.updatePagination(response);
                this.lastUserResult = response;
                this.users = response.results;
            });
        }
    }

    toggleUserSelect(user) {
        if (this.selected.has(user.id)) {
            this.selected = this.selected.delete(user.id);
        } else {
            this.selected = this.selected.add(user.id);
        }
    }

    addUsers() {
        delete this.error;
        let promises = this.selected.toArray().map((userId) => {
            if (this.resolve.adminView === 'team') {
                return this.teamService.addUser(
                    this.resolve.platformId,
                    this.resolve.organizationId,
                    this.resolve.teamId,
                    userId
                );
            } else if (this.resolve.adminView === 'organization') {
                return this.organizationService.addUser(
                    this.resolve.platformId,
                    this.resolve.organizationId,
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
}


const AddUserModalModule = angular.module('components.settings.addUserModal', []);

AddUserModalModule.component('rfAddUserModal', AddUserModalComponent);
AddUserModalModule.controller('AddUserModalController', AddUserModalController);

export default AddUserModalModule;
