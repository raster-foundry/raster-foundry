import angular from 'angular';
import tpl from './navbarSearch.html';

const NavbarSearchComponent = {
    templateUrl: tpl,
    controller: 'NavbarSearchController'
};

class NavbarSearchController {
    constructor($state, $scope, organizationService, userService) {
        'ngInject';
        this.$state = $state;
        this.$scope = $scope;
        this.organizationService = organizationService;
        this.userService = userService;
    }

    $onInit() {
        this.$scope.$watch('$ctrl.searchText', this.onSearch.bind(this));
        this.searchQueries = {};
    }

    clearSearch() {
        this.searchText = '';
        delete this.searchResults;
        delete this.lastRequest;
        delete this.loading;
    }

    onSearch(text) {
        if (text && text.length) {
            this.lastRequest = Date.now();
            let thisRequest = this.lastRequest;
            this.loading = true;
            switch (this.searchType) {
            case 'Organizations':
                this.organizationService.searchOrganizations(text).then((response) => {
                    if (this.lastRequest === thisRequest) {
                        this.loading = false;
                    }
                    if (this.searchText && this.searchText.length) {
                        this.searchResults = response.map(
                            (org) => ({
                                name: org.name || org.id,
                                avatar: org.logoUri,
                                state: `admin.organization({organizationId: '${org.id}'})`
                            })
                        );
                    }
                });
                break;
            case 'Users':
                this.userService.searchUsers(text).then((response) => {
                    if (this.lastRequest === thisRequest) {
                        this.loading = false;
                    }
                    if (this.searchText && this.searchText.length) {
                        this.searchResults = response.map(
                            (user) => ({
                                name: user.name || user.email || user.id,
                                avatar: user.profileImageUri,
                                state: `user({userId: '${user.id}'})`
                            })
                        );
                    }
                });
                break;
            default:
                throw new Error('Invalid select option for navbar search dropdown selected');
            }
        } else {
            delete this.searchResults;
        }
    }
}

const NavbarSearchModule = angular.module('components.common.navbarSearch', []);

NavbarSearchModule.component('rfNavbarSearch', NavbarSearchComponent);
NavbarSearchModule.controller('NavbarSearchController', NavbarSearchController);

export default NavbarSearchModule;
