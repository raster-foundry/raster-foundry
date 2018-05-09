/* globals BUILDCONFIG */

export default (app) => {
    class TeamService {
        constructor(
            $resource, $q,
            authService
        ) {
            'ngInject';
            this.authService = authService;
            this.$q = $q;
            this.Team = $resource(
                `${BUILDCONFIG.API_HOST}/api/platforms/:platformId/` +
                    'organizations/:organizationId/teams/:teamId',
                {
                    platformId: '@platformId',
                    organizationId: '@organizationId',
                    teamId: '@teamId'
                }, {
                    members: {
                        url: `${BUILDCONFIG.API_HOST}/api/platforms/:platformId/` +
                            'organizations/:organizationId/teams/:teamId/members',
                        method: 'GET'
                    }
                }
            );
        }

        getMembers(platformId, organizationId, teamId, page, search) {
            return this.Team.members(
                {platformId, organizationId, teamId, page, search}
            ).$promise;
        }
    }

    app.service('teamService', TeamService);
};
