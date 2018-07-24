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
                get: {
                    url: `${BUILDCONFIG.API_HOST}/api/users/:userId`,
                    method: 'GET',
                    params: {
                        userId: '@userId'
                    }
                },
                update: {
                    method: 'PUT',
                    cache: false
                },
                getTeams: {
                    url: `${BUILDCONFIG.API_HOST}/api/users/me/teams`,
                    method: 'GET',
                    cache: false,
                    isArray: true
                },
                search: {
                    url: `${BUILDCONFIG.API_HOST}/api/users/search`,
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

        updateOwnUser(user) {
            return this.User.update(user).$promise;
        }

        getTeams() {
            return this.User.getTeams().$promise;
        }

        getUserById(id) {
            return this.User.get({userId: id}).$promise;
        }

        searchUsers(searchText) {
            return this.User.search({search: searchText}).$promise;
        }
    }

    app.service('userService', UserService);
};
