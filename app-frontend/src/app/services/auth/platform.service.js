/* globals BUILDCONFIG */

export default (app) => {
    class PlatformService {
        constructor(
            $resource
        ) {
            'ngInject';
            this.Platform = $resource(
                `${BUILDCONFIG.API_HOST}/api/platforms/:id`,
                {id: '@id'}, {
                    members: {
                        url: `${BUILDCONFIG.API_HOST}/api/platforms/:id/members`,
                        method: 'GET'
                    },
                    organizations: {
                        url: `${BUILDCONFIG.API_HOST}/api/platforms/:id/organizations`,
                        method: 'GET'
                    },
                    createOrganization: {
                        url: `${BUILDCONFIG.API_HOST}/api/platforms/:id/organizations`,
                        method: 'POST'
                    },
                    setPlatformStatus: {
                        url: `${BUILDCONFIG.API_HOST}/api/platforms/:id/`,
                        method: 'POST'
                    },
                    updatePlatform: {
                        method: 'PUT',
                        params: {
                            id: '@id'
                        }
                    }
                }
            );
        }

        getPlatform(platformId) {
            return this.Platform
                .get({id: platformId})
                .$promise;
        }

        getMembers(platformId, page, search) {
            return this.Platform
                .members({id: platformId, page, search, pageSize: 10})
                .$promise;
        }

        getOrganizations(platformId, page, search) {
            return this.Platform
                .organizations({id: platformId, page, search, pageSize: 10})
                .$promise;
        }


        createOrganization(platformId, name) {
            return this.Platform
                .createOrganization({id: platformId}, {platformId, name})
                .$promise;
        }

        deactivatePlatform(platformId) {
            return this.Platform.setPlatformStatus({id: platformId}, {isActive: false});
        }

        updatePlatform(params) {
            return this.Platform.updatePlatform(params).$promise;
        }
    }

    app.service('platformService', PlatformService);
};
