export default (app) => {
    class TokenService {
        constructor($resource) {
            'ngInject';

            this.Token = $resource(
                '/api/tokens/:id', {
                    id: '@id'
                }, {
                    query: {
                        method: 'GET',
                        cache: false,
                        isArray: true
                    },
                    delete: {
                        method: 'DELETE'
                    }
                }
            );
        }

        query(params = {}) {
            return this.Token.query(params).$promise;
        }

        delete(params) {
            return this.Token.delete(params).$promise;
        }
    }

    app.service('tokenService', TokenService);
};
