/* globals BUILDCONFIG */
import _ from 'lodash';

export default (app) => {
    class TemplateService {
        constructor(
            $resource, $http, $q,
            authService
        ) {
            this.$http = $http;
            this.$q = $q;
            this.authService = authService;

            this.Template = $resource(
                `${BUILDCONFIG.API_HOST}/api/templates/:id/`, {
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
                    update: {
                        method: 'PUT',
                        url: `${BUILDCONFIG.API_HOST}/api/templates/:id`
                    },
                    delete: {
                        method: 'DELETE'
                    }
                }
            );
        }

        fetchTemplates(params = {}) {
            return this.Template.query(params).$promise;
        }

        getTemplate(id) {
            return this.Template.get({id}).$promise;
        }

        getAnalysis(template) {
            return angular.copy(template.latestVersion.analysis);
        }

        updateTemplate(template) {
            let update = _.cloneDeep(template);
            return this.Template.update({id: template.id}, update).$promise;
        }
    }

    app.service('templateService', TemplateService);
};
