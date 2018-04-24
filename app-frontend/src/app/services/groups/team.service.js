/* globals BUILDCONFIG */

export default (app) => {
    class TeamService {
        constructor(
            $resource, authService
        ) {
            'ngInject';

            this.authService = authService;

            this.Team = $resource(
                `${BUILDCONFIG.API_HOST}/api/teams/:id/`, {
                    id: '@properties.id'
                }, {
                    query: {
                        method: 'GET',
                        cache: false
                    },
                    get: {
                        method: 'GET',
                        cache: false
                    },
                    create: {
                        method: 'POST'
                    },
                    delete: {
                        method: 'DELETE'
                    },
                    update: {
                        method: 'PUT',
                        url: `${BUILDCONFIG.API_HOST}/api/teams/:id`,
                        params: {
                            id: '@id'
                        }
                    }
                }
            );
        }

        query(params = {}) {
            return this.Team.query(params).$promise;
        }

        get(id) {
            return this.Team.get({id}).$promise;
        }

        createTeam(name, settings = {}) {
            return this.authService.getCurrentUser().then(
                (user) => {
                    return this.Team.create({
                        organizationId: user.organizationId,
                        name: name,
                        settings: settings
                    }).$promise;
                },
                (error) => {
                    return error;
                }
            );
        }

        updateTeam(params) {
            return this.Team.update(params).$promise;
        }

        deleteTeam(params) {
            return this.Team.delete(params).$promise;
        }
    }

    app.service('teamService', TeamService);
};
