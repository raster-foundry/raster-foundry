/* globals BUILDCONFIG */

import angular from 'angular';

export default (app) => {
    class PermissionsService {
        constructor($resource, $q, authService) {
            this.$q = $q;
            this.authService = authService;
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

        getEditableObjectPermissions(permissionsBase, objectType, object) {
            return this.$q((resolve, reject) => {
                const user = this.authService.user;
                const roleSubjectIds = this.authService.userRoles.map(r => r.groupId);
                const matchingIds = [user.id, ...roleSubjectIds];
                if (object.owner === user.id || object.owner.id === user.id) {
                    resolve([{actionType: '*'}]);
                } else {
                    this.query({
                        permissionsBase,
                        objectType,
                        objectId: object.id
                    }).$promise.then(permissions => {
                        resolve(permissions.filter(p => matchingIds.includes(p.subjectId)));
                    }).catch((e) => {
                        // can't view permissions, don't have edit
                        if (e.status === 403) {
                            resolve([]);
                        } else {
                            reject(e);
                        }
                    });
                }
            });
        }
    }

    app.service('permissionsService', PermissionsService);
};
