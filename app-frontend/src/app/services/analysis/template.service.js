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
                    id: '@properties.id',
                    templateId: '@properties.templateId'
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
                    },
                    publish: {
                        method: 'POST',
                        url: `${BUILDCONFIG.API_HOST}/api/templates/:templateId/publish`
                    }
                }
            );
        }

        createTemplate(template) {
            return this.authService.getCurrentUser().then(
                user => {
                    const templateDefaults = {
                        organizationId: user.organizationId,
                        details: '',
                        description: '',
                        requirements: '',
                        visibility: 'PRIVATE',
                        compatibleDataSources: [],
                        tags: [],
                        categories: [],
                        owner: user.id
                    };
                    return this.Template.create(Object.assign(templateDefaults, template)).$promise;
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

        publishTemplateVersion(templateId, templateVersion) {
            const templateVersionDefaults = {
                description: '',
                changelog: '',
                templateId
            };

            return this.Template.publish({templateId},
                Object.assign({}, templateVersionDefaults, templateVersion, {templateId})
            ).$promise;
        }

        deleteTemplate(templateId) {
            return this.Template.delete({id: templateId}).$promise;
        }
    }

    app.service('templateService', TemplateService);
};
