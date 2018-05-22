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
                        method: 'POST',
                        isArray: true
                    },
                    deactivateUser: {
                        url: `${BUILDCONFIG.API_HOST}/api/platforms/:platformId/` +
                            'organizations/:organizationId/members/:userId',
                        method: 'DELETE'
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
                    deactivateOrganization: {
                        url:
                        `${BUILDCONFIG.API_HOST}/api/platforms/:platformId/` +
                            'organizations/:organizationId',
                        method: 'DELETE',
                        params: {platformId: '@platformId', organizationId: '@organizationId'}
                    }
                }
            );

            this.Organization = $resource(
                `${BUILDCONFIG.API_HOST}/api/organizations/:organizationId`,
                {
                    organizationId: '@organizationId'
                }
            );
        }

        addUser(platformId, organizationId, userId) {
            return this.PlatformOrganization.addUser({platformId, organizationId}, {
                userId,
                groupRole: 'MEMBER'
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
                .deactivateOrganization({platformId, organizationId})
                .$promise;
        }
    }

    app.service('organizationService', OrganizationService);
};
