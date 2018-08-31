/* globals BUILDCONFIG */

import angular from 'angular';

export default (app) => {
    class PermissionsService {
        constructor($resource) {
            this.Permissions = $resource(
                `${BUILDCONFIG.API_HOST}/api/:permissionsBase/:objectId/permissions`, {
                    permissionsBase: '@permissionsBase',
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

        query({permissionsBase, objectId}) {
            return this.Permissions.query({permissionsBase, objectId});
        }

        create({permissionsBase, objectId}, objectAccessControlRule) {
            return this.Permissions.create(
                Object.assign(objectAccessControlRule, {permissionsBase, objectId})
            ).$promise;
        }

        update({permissionsBase, objectId}, objectAccessControlRuleList) {
            return this.Permissions.update(
                Object.assign(
                    {rules: objectAccessControlRuleList},
                    {permissionsBase, objectId}
                )
            ).$promise;
        }

        delete({permissionsBase, objectId}) {
            return this.Permissions.delete(
                Object.assign(
                    { permissionsBase, objectId }
                )
            ).$promise;
        }
    }

    app.service('permissionsService', PermissionsService);
};
