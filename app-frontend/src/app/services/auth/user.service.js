/* globals BUILDCONFIG */

export default (app) => {
    class UserService {
        constructor(
            $resource, $q, localStorage,
            authService, APP_CONFIG
        ) {
            'ngInject';
            this.authService = authService;
            this.localStorage = localStorage;
            this.$q = $q;

            this.UserMetadata = $resource(
                'https://' + APP_CONFIG.auth0Domain +
                    '/api/v2/users/:userid',
                {userid: '@id'},
                {
                    get: {
                        method: 'GET',
                        cache: false
                    },
                    patch: {
                        method: 'PATCH',
                        cache: false
                    }
                });
            this.User = $resource(`${BUILDCONFIG.API_HOST}/api/users/me`, { }, {
                update: {
                    method: 'PUT',
                    cache: false,
                    /* eslint-disable */
                    transformRequest: (data, headers) => {
                        headers = angular.extend(
                            {}, headers, {'Content-Type': 'application/json'});
                        return angular.toJson(data);
                    /* eslint-enable */
                    }
                },
                getTeams: {
                    url: `${BUILDCONFIG.API_HOST}/api/users/me/teams`,
                    method: 'GET',
                    cache: false,
                    isArray: true
                }
            });
        }

        updateUserMetadata(userdata) {
            let id = this.authService.getProfile().sub;
            return this.UserMetadata.patch(
                {userid: id},
                {user_metadata: userdata} // eslint-disable-line camelcase
            ).$promise.then((res) => {
                // TODO toast this!
                this.localStorage.set('profile', res);
                return res;
            }, (err) => err);
        }

        updatePlanetToken(token) {
            return this.$q((resolve, reject) => {
                this.User.update(token).$promise.then(() => {
                    resolve();
                }, (err) => reject(err));
            });
        }

        getTeams() {
            return this.User.getTeams().$promise;
        }

        getUserById(id) {
            return this.UserMetadata.get({userid: id}).$promise;
        }
    }

    app.service('userService', UserService);
};
