export default (app) => {
    class SceneService {
        constructor($resource) {
            'ngInject';

            this.Scene = $resource(
                '/api/scenes/:id/', {
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
            return this.Scene.query(params).$promise;
        }
    }

    app.service('sceneService', SceneService);
};
