export default (app) => {
    class BucketService {
        constructor($resource, userService, $http) {
            'ngInject';
            this.userService = userService;
            this.$http = $http;

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
                    addScenes: {
                        method: 'POST',
                        url: '/api/buckets/:bucketId/scenes/',
                        params: {
                            bucketId: '@bucketId'
                        },
                        isArray: true
                    },
                    bucketScenes: {
                        method: 'GET',
                        cache: false,
                        url: '/api/buckets/:bucketId/scenes',
                        params: {
                            bucketId: '@bucketId'
                        }
                    },
                    removeScenes: {
                        method: 'DELETE',
                        url: '/api/buckets/:bucketId/scenes/',
                        params: {
                            bucketId: '@bucketId'
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

        addScenes(bucketId, sceneIds) {
            return this.Bucket.addScenes(
                {bucketId: bucketId},
                sceneIds
            ).$promise;
        }

        getBucketScenes(params) {
            return this.Bucket.bucketScenes(params).$promise;
        }

        getBucketSceneCount(bucketId) {
            return this.Bucket.bucketScenes({bucketId: bucketId, limit: 1}).$promise;
        }

        removeScenesFromBucket(bucketId, scenes) {
            return this.$http({
                method: 'DELETE',
                url: `/api/buckets/${bucketId}/scenes/`,
                data: scenes,
                headers: {'Content-Type': 'application/json;charset=utf-8'}
            });
        }

        deleteBucket(bucketId) {
            return this.Bucket.delete({id: bucketId}).$promise;
        }
    }

    app.service('bucketService', BucketService);
};
