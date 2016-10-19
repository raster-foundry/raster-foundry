export default (app) => {
    class BucketService {
        constructor($resource, userService) {
            'ngInject';
            this.userService = userService;

            this.Bucket = $resource(
                '/api/buckets/:id/', {
                    id: '@properties.id'
                }, {
                    query: {
                        method: 'GET',
                        cache: false
                    },
                    get: {
                        method: 'GET',
                        cache: false
                    },
                    create: {
                        method: 'POST'
                    },
                    delete: {
                        method: 'DELETE'
                    },
                    addScene: {
                        method: 'POST',
                        url: '/api/buckets/:bucketId/scenes/:sceneId',
                        params: {
                            bucketId: '@bucketId', sceneId: '@sceneId'
                        }
                    },
                    bucketScenes: {
                        method: 'GET',
                        cache: false,
                        url: '/api/buckets/:bucketId/scenes',
                        params: {
                            bucketId: '@bucketId'
                        }
                    },
                    removeScene: {
                        method: 'DELETE',
                        url: '/api/buckets/:bucketId/scenes/:sceneId',
                        params: {
                            bucketId: '@bucketId',
                            sceneId: '@sceneId'
                        }
                    }
                }
            );
        }

        query(params = {}) {
            return this.Bucket.query(params).$promise;
        }

        createBucket(name) {
            return this.userService.getCurrentUser().then(
                (user) => {
                    let publicOrg = user.organizations.filter(
                        (org) => org.name === 'Public'
                    )[0];
                    return this.Bucket.create({
                        organizationId: publicOrg.id, name: name, description: '',
                        visibility: 'PRIVATE', tags: []
                    }).$promise;
                },
                (error) => {
                    return error;
                }
            );
        }

        addScene(bucketId, sceneId) {
            return this.Bucket.addScene(
                {bucketId: bucketId, sceneId: sceneId},
                {}
            ).$promise;
        }

        getBucketScenes(params) {
            return this.Bucket.bucketScenes(params).$promise;
        }

        getBucketSceneCount(bucketId) {
            return this.Bucket.bucketScenes({bucketId: bucketId, limit: 1}).$promise;
        }

        removeSceneFromBucket(params) {
            return this.Bucket.removeScene(params).$promise;
        }

        deleteBucket(bucketId) {
            return this.Bucket.delete({id: bucketId}).$promise;
        }
    }

    app.service('bucketService', BucketService);
};
