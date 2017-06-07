export default (app) => {
    class aoiService {
        constructor($resource) {
            'ngInject';
            this.AOI = $resource(
                '/api/areas-of-interest/:id', {id: '@id'}, {
                    update: {
                        method: 'PUT'
                    }
                }
            );
        }
        updateAOI(params) {
            return this.AOI.update(params).$promise;
        }
    }

    app.service('aoiService', aoiService);
};
