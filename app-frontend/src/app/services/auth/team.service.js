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
            this.PlatformTeam = $resource(
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
                    },
                    create: {
                        url: `${BUILDCONFIG.API_HOST}/api/platforms/:platformId/` +
                            'organizations/:organizationId/teams/',
                        method: 'POST'
                    },
                    update: {
                        url:
                        `${BUILDCONFIG.API_HOST}/api/platforms/:platformId/` +
                            'organizations/:organizationId/teams/:teamId',
                        method: 'PUT',
                        params: {
                            platformId: '@platformId',
                            organizationId: '@organizationId',
                            teamId: '@teamId'
                        }
                    }
                }
            );
            this.Team = $resource(
                `${BUILDCONFIG.API_HOST}/api/teams/:teamId/`,
                {
                    teamId: '@teamId'
                }, {
                    addUser: {
                        url: `${BUILDCONFIG.API_HOST}/api/platforms/:platformId/` +
                            'organizations/:organizationId/teams/:teamId/members',
                        method: 'POST'
                    },
                    search: {
                        url: `${BUILDCONFIG.API_HOST}/api/platforms/:platformId/teams/search`,
                        method: 'GET',
                        cache: false,
                        isArray: true
                    },
                    removeUser: {
                        url: `${BUILDCONFIG.API_HOST}/api/platforms/:platformId/` +
                            'organizations/:organizationId/teams/:teamId/members/:userId',
                        method: 'DELETE',
                        params: {
                            userId: '@userId'
                        },
                        isArray: true
                    }
                }
            );
        }

        addUser(platformId, organizationId, teamId, userId) {
            return this.Team.addUser({platformId, organizationId, teamId}, {
                userId,
                groupRole: 'MEMBER'
            }).$promise;
        }

        addUserWithRole(platformId, organizationId, teamId, groupRole, userId) {
            return this.Team.addUser(
                { platformId, organizationId, teamId },
                { userId, groupRole }
            ).$promise;
        }

        setUserRole(platformId, organizationId, teamId, user) {
            return this.Team.addUser({
                platformId,
                organizationId,
                teamId
            }, {
                userId: user.id,
                groupRole: user.groupRole
            }).$promise;
        }

        removeUser(platformId, organizationId, teamId, userId) {
            return this.Team.removeUser({platformId, organizationId, teamId, userId}).$promise;
        }

        getTeam(teamId) {
            return this.Team.get({teamId}).$promise;
        }

        getMembers(platformId, organizationId, teamId, page, search) {
            return this.PlatformTeam.members(
                {platformId, organizationId, teamId, page, search, pageSize: 10}
            ).$promise;
        }

        createTeam(platformId, organizationId, teamName) {
            return this.PlatformTeam
                .create(
                    {platformId, organizationId},
                    {organizationId, name: teamName, settings: {}}
                )
                .$promise;
        }

        deactivateTeam(platformId, organizationId, teamId) {
            return this.PlatformTeam.delete({platformId, organizationId, teamId}).$promise;
        }

        updateTeam(platformId, organizationId, teamId, params) {
            return this.PlatformTeam.update({platformId, organizationId, teamId}, params).$promise;
        }
        searchTeams(searchText, platformId) {
            return this.Team.search({search: searchText, platformId}).$promise;
        }
    }

    app.service('teamService', TeamService);
};
