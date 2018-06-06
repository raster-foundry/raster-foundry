/* globals BUILDCONFIG */

import angular from 'angular';

export default (app) => {
    class PermissionsService {
        constructor($resource) {
            this.Permissions = $resource(
                `${BUILDCONFIG.API_HOST}/api/:objectType/:objectId/permissions`, {
                    objectType: '@objectType',
                    objectId: '@objectId'
                }, {
                    query: {
                        method: 'GET',
                        cache: false,
                        isArray: true
                    },
                    create: {
                        method: 'POST',
                        isArray: true
                    },
                    update: {
                        method: 'PUT',
                        cache: false,
                        isArray: true,
                        transformRequest: (reqBody) => angular.toJson(reqBody.rules)
                    },
                    delete: {
                        method: 'DELETE'
                    }
                }
            );
        }

        query({objectType, objectId}) {
            return this.Permissions.query({objectType: objectType, objectId: objectId});
        }

        create({objectType, objectId}, accessControlRuleCreate) {
            return this.Permissions.create(
                Object.assign(accessControlRuleCreate, {objectType: objectType, objectId: objectId})
            ).$promise;
        }

        update({objectType, objectId}, accessControlRuleCreates) {
            return this.Permissions.update(
                Object.assign(
                    {rules: accessControlRuleCreates},
                    {objectType: objectType, objectId: objectId}
                )
            ).$promise;
        }

        delete({objectType, objectId}) {
            return this.Permissions.delete(
                Object.assign(
                    { objectType, objectId }
                )
            ).$promise;
        }
    }

    app.service('permissionsService', PermissionsService);
};
