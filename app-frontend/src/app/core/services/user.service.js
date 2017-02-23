export default (app) => {
    class UserService {
        constructor($resource, authService, APP_CONFIG, localStorage) {
            'ngInject';
            this.authService = authService;
            this.localStorage = localStorage;
            this.User = $resource('/api/users/:id', {
                id: '@id'
            }, {
                query: {
                    method: 'GET',
                    cache: false
                },
                get: {
                    method: 'GET',
                    cache: false
                }
            });
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
        }

        getCurrentUser() {
            let id = this.authService.profile().user_id;
            return this.User.get({id: id}).$promise;
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
    }

    app.service('userService', UserService);
};
