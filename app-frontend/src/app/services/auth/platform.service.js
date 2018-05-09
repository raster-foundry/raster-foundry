/* globals BUILDCONFIG */

export default (app) => {
    class PlatformService {
        constructor(
            $resource
        ) {
            'ngInject';
            this.platform = $resource(
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
                    }
                }
            );
        }

        getPlatform(platformId) {
            return this.platform.get({id: platformId}).$promise;
        }

        getMembers(platformId, page, search) {
            return this.platform.members({id: platformId, page, search}).$promise;
        }

        getOrganizations(platformId, page, search) {
            return this.platform.organizations({id: platformId, page, search}).$promise;
        }


        createOrganization(platformId, name) {
            return this.platform.createOrganization({id: platformId}, {platformId, name}).$promise;
        }
    }

    app.service('platformService', PlatformService);
};
