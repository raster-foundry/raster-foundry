export default (app) => {
    class UserService {
        constructor($resource, authService, APP_CONFIG, localStorage) {
            'ngInject';
            this.authService = authService;
            this.localStorage = localStorage;

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
