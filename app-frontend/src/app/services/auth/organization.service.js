/* globals BUILDCONFIG */

export default (app) => {
    class OrganizationService {
        constructor(
            $resource
        ) {
            this.PlatformOrganization = $resource(
                `${BUILDCONFIG.API_HOST}/api/platforms/:platformId/` +
                    'organizations/:organizationId',
                {
                    platformId: '@platformId',
                    organizationId: '@organizationId'
                }, {
                    members: {
                        url: `${BUILDCONFIG.API_HOST}/api/platforms/:platformId/` +
                            'organizations/:organizationId/members',
                        method: 'GET'
                    },
                    addUser: {
                        url: `${BUILDCONFIG.API_HOST}/api/platforms/:platformId/` +
                            'organizations/:organizationId/members',
                        method: 'POST'
                    },
                    deactivateUser: {
                        url: `${BUILDCONFIG.API_HOST}/api/platforms/:platformId/` +
                            'organizations/:organizationId/members/:userId',
                        method: 'DELETE',
                        params: {
                            userId: '@userId'
                        },
                        isArray: true
                    },
                    teams: {
                        url: `${BUILDCONFIG.API_HOST}/api/platforms/:platformId/` +
                            'organizations/:organizationId/teams',
                        method: 'GET'
                    },
                    createTeam: {
                        url: `${BUILDCONFIG.API_HOST}/api/platforms/:platformId/organizations`,
                        method: 'POST'
                    },
                    setOrganizationStatus: {
                        url:
                        `${BUILDCONFIG.API_HOST}/api/platforms/:platformId/` +
                            'organizations/:organizationId',
                        method: 'POST',
                        params: {platformId: '@platformId', organizationId: '@organizationId'},
                        /* eslint-disable */
                        transformRequest: (data, headers) => {
                            headers = angular.extend(
                                {}, headers, {'Content-Type': 'application/json'});
                            return angular.toJson(data);
                        /* eslint-enable */
                        }
                    },
                    updateOrganization: {
                        url:
                        `${BUILDCONFIG.API_HOST}/api/platforms/:platformId/` +
                            'organizations/:organizationId',
                        method: 'PUT',
                        params: {platformId: '@platformId', organizationId: '@organizationId'}
                    }
                }
            );

            this.Organization = $resource(
                `${BUILDCONFIG.API_HOST}/api/organizations/:organizationId`,
                {
                    organizationId: '@organizationId'
                }, {
                    addOrganizationLogo: {
                        url: `${BUILDCONFIG.API_HOST}/api/organizations/:organizationId/logo`,
                        method: 'POST',
                        /* eslint-disable */
                        transformRequest: (data, headers) => {
                            headers = angular.extend(
                                {}, headers, {'Content-Type': 'application/json'});
                            return angular.toJson(data);
                        /* eslint-enable */
                        }
                    },
                    search: {
                        url: `${BUILDCONFIG.API_HOST}/api/organizations/search`,
                        method: 'GET',
                        cache: false,
                        isArray: true
                    }
                }
            );
        }

        addUser(platformId, organizationId, userId) {
            return this.PlatformOrganization.addUser({platformId, organizationId}, {
                userId,
                groupRole: 'MEMBER'
            }).$promise;
        }

        addUserWithRole(platformId, organizationId, groupRole, userId) {
            return this.PlatformOrganization.addUser(
                { platformId, organizationId },
                { userId, groupRole }
            ).$promise;
        }

        approveUserMembership(platformId, organizationId, userId, groupRole) {
            return this.PlatformOrganization.addUser({platformId, organizationId}, {
                userId, groupRole
            }).$promise;
        }

        setUserRole(platformId, organizationId, user) {
            return this.PlatformOrganization.addUser({
                platformId,
                organizationId
            }, {
                userId: user.id,
                groupRole: user.groupRole
            }).$promise;
        }

        getOrganization(organizationId) {
            return this.Organization.get({organizationId}).$promise;
        }

        getMembers(platformId, organizationId, page, searchText) {
            let search = null;
            if (searchText && searchText.length) {
                search = searchText;
            }

            return this.PlatformOrganization
                .members({platformId, organizationId, page, search, pageSize: 10})
                .$promise;
        }

        getTeams(platformId, organizationId, page, searchText) {
            let search = null;
            if (searchText && searchText.length) {
                search = searchText;
            }

            return this.PlatformOrganization
                .teams({platformId, organizationId, page, search, pageSize: 10})
                .$promise;
        }

        deactivate(platformId, organizationId) {
            return this.PlatformOrganization
                .setOrganizationStatus({platformId, organizationId}, 'INACTIVE').$promise;
        }

        activate(platformId, organizationId) {
            return this.PlatformOrganization
                .setOrganizationStatus({platformId, organizationId}, 'ACTIVE').$promise;
        }

        addOrganizationLogo(organizationId, logoBase64) {
            return this.Organization
                .addOrganizationLogo({organizationId}, logoBase64)
                .$promise;
        }

        updateOrganization(platformId, organizationId, params) {
            return this.PlatformOrganization
                .updateOrganization({platformId, organizationId}, params).$promise;
        }

        removeUser(platformId, organizationId, userId) {
            return this.PlatformOrganization
                .deactivateUser({platformId, organizationId, userId}).$promise;
        }

        searchOrganizations(searchText) {
            return this.Organization.search({search: searchText}).$promise;
        }
    }

    app.service('organizationService', OrganizationService);
};
