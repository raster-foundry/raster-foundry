import tpl from './index.html';

class ProjectPublishingController {
    constructor(
        $rootScope, $q, $log, $window, $state, $timeout,
        projectService, tokenService, authService
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        this.tileUrl = this.projectService.getProjectTileURL(this.project);
        this.urlMappings = {
            standard: {
                label: 'Standard',
                z: 'z',
                x: 'x',
                y: 'y'
            },
            arcGIS: {
                label: 'ArcGIS',
                z: 'level',
                x: 'col',
                y: 'row'
            }
        };

        let sharePolicies = [
            {
                label: 'Private',
                description:
                `Only you and those you create tokens for
                 will be able to view tiles for this project`,
                enum: 'PRIVATE',
                active: false,
                enabled: true,
                token: true
            },
            {
                label: 'Organization',
                description:
                `Users in your organization will be able to use
                 their own tokens to view tiles for this project`,
                enum: 'ORGANIZATION',
                active: false,
                enabled: false,
                token: true
            },
            {
                label: 'Public',
                description: 'Anyone can view tiles for this project without a token',
                enum: 'PUBLIC',
                active: false,
                enabled: true,
                token: false
            }
        ];

        if (!this.templateTitle) {
            this.project = this.project;
            this.sharePolicies = sharePolicies.map(
                (policy) => {
                    let isActive = policy.enum === this.project.tileVisibility;
                    policy.active = isActive;
                    return policy;
                }
            );
            this.activePolicy = this.sharePolicies.find((policy) => policy.active);
            this.updateShareUrl();
        }

        this.tileLayerUrls = {
            standard: null,
            arcGIS: null
        };

        this.hydrateTileUrls();
    }

    updateShareUrl() {
        this.projectService.getProjectShareURL(this.project).then((url) => {
            this.shareUrl = url;
        });
    }

    onPolicyChange(policy) {
        let shouldUpdate = true;

        let oldPolicy = this.activePolicy;
        if (this.activePolicy) {
            this.activePolicy.active = false;
        } else {
            shouldUpdate = false;
        }

        this.activePolicy = policy;
        policy.active = true;

        this.project.tileVisibility = policy.enum;
        this.project.visibility = policy.enum;

        if (shouldUpdate) {
            if (this.project.owner.id) {
                this.project.owner = this.project.owner.id;
            }
            this.projectService.updateProject(this.project).then((res) => {
                this.$log.debug(res);
            }, (err) => {
                // TODO: Toast this
                this.$log.debug('Error while updating project share policy', err);
                this.activePolicy.active = false;
                oldPolicy.active = true;
                this.activePolicy = oldPolicy;
            });
        }
        this.hydrateTileUrls();
    }

    hydrateTileUrls() {
        let zxyUrl = this.tileUrl
            .replace('{z}', `{${this.urlMappings.standard.z}}`)
            .replace('{x}', `{${this.urlMappings.standard.x}}`)
            .replace('{y}', `{${this.urlMappings.standard.y}}`);
        let arcGISUrl = this.tileUrl
            .replace('{z}', `{${this.urlMappings.arcGIS.z}}`)
            .replace('{x}', `{${this.urlMappings.arcGIS.x}}`)
            .replace('{y}', `{${this.urlMappings.arcGIS.y}}`);

        if (this.templateTitle) {
            this.tileLayerUrls.arcGIS = `${arcGISUrl}`;
            this.tileLayerUrls.standard = `${zxyUrl}`;
            this.analysisToken = this.tileUrl.split('?mapToken=')[1].split('&node=')[0];
        } else {
            // eslint-disable-next-line no-lonely-if
            if (this.activePolicy && this.activePolicy.enum !== 'PRIVATE') {
                this.tileLayerUrls.arcGIS = `${arcGISUrl}`;
                this.tileLayerUrls.standard = `${zxyUrl}`;
            } else {
                this.tokenService.getOrCreateProjectMapToken(this.project).then(
                    (mapToken) => {
                        this.mapToken = mapToken;
                        this.tileLayerUrls.standard = `${zxyUrl}&mapToken=${mapToken.id}`;
                        this.tileLayerUrls.arcGIS = `${arcGISUrl}&mapToken=${mapToken.id}`;
                    }
                );
            }
        }
    }

    onCopyClick(e, url, type) {
        if (url && url.length) {
            this.copyType = type;
            this.$timeout(() => {
                delete this.copyType;
            }, 1000);
        }
    }
}

const component = {
    bindings: {
        project: '<',
        tileUrl: '<',
        templateTitle: '<'
    },
    templateUrl: tpl,
    controller: ProjectPublishingController.name
};

export default angular
    .module('components.pages.projects.settings.publishing', [])
    .controller(ProjectPublishingController.name, ProjectPublishingController)
    .component('rfProjectPublishingPage', component)
    .name;
