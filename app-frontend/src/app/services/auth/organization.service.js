/* globals BUILDCONFIG */

export default (app) => {
    class OrganizationService {
        constructor(
            $resource
        ) {
            this.platform = $resource(
                `${BUILDCONFIG.API_HOST}/api/platforms/:platformId/` +
                    'members/:organizationId',
                {
                    platformId: '@platformId',
                    organizationId: '@organizationId'
                }, {
                    members: {
                        url: `${BUILDCONFIG.API_HOST}/api/platforms/:platformId/` +
                            'organizations/:organizationId/members',
                        method: 'GET'
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
        }

        deactivate(platformId, organizationId) {
            return this.platform.deactivateOrganization({platformId, organizationId}).$promise;
        }
    }

    app.service('organizationService', OrganizationService);
};
