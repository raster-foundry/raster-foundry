export default (app) => {
    class UserService {
        constructor($resource, auth) {
            'ngInject';
            this.auth = auth;

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
        }

        getCurrentUser() {
            let id = this.auth.profile.user_id;
            return this.User.get({id: id}).$promise;
        }
    }

    app.service('userService', UserService);
};
