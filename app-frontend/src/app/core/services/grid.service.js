export default (app) => {
    class GridService {
        constructor($resource) {
            'ngInject';

            this.Grid = $resource(
                '/api/scene-grid/:bbox/:zoom', {
                    bbox: '@properties.bbox',
                    zoom: '@properties.zoom'
                }, {
                    query: {
                        method: 'GET',
                        cache: false
                    },
                    get: {
                        method: 'GET',
                        cache: true
                    }
                }
            );
        }

        query(params = {}) {
            return this.Grid.query(params).$promise;
        }
    }

    app.service('gridService', GridService);
};
