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
                    cache: false
                }
            });
        }

        updateUserMetadata(userdata) {
            let id = this.authService.profile().user_id;
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
                this.authService.getCurrentUser().then((user) => {
                    user.planetCredential = token;
                    this.User.update(user).$promise.then(() => {
                        resolve();
                    }, (err) => reject(err));
                }, (err) => reject(err));
            });
        }
    }

    app.service('userService', UserService);
};
