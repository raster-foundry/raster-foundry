export default (app) => {
    class OrganizationService {
        constructor($resource) {
            'ngInject';

            this.Organization = $resource(
                '/api/organizations/:id/', {
                    id: '@properties.id'
                }, {
                    query: {
                        method: 'GET',
                        cache: false
                    },
                    get: {
                        method: 'GET',
                        cache: false
                    }
                }
            );
        }

        query(params = {}) {
            return this.Organization.query(params).$promise;
        }
    }

    app.service('organizationService', OrganizationService);
};
