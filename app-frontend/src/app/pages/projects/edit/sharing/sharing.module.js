import angular from 'angular';

const sharePolicies = [
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

const urlMappings = {
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

class SharingController {
    constructor(
        $log, $state, $window, $timeout,
        projectService, projectEditService, tokenService
    ) {
        'ngInject';
        this.$log = $log;
        this.$state = $state;
        this.$window = $window;
        this.$timeout = $timeout;

        this.projectService = projectService;
        this.projectEditService = projectEditService;
        this.tokenService = tokenService;
    }

    $onInit() {
        this.projectEditService
            .fetchCurrentProject()
            .then(project => this.initProject(project));
    }

    initProject(project) {
        this.project = project;
        this.initSharePolicies();
        this.initShareURL();
        this.initTileURL();
    }

    initSharePolicies() {
        if (this.project) {
            this.sharePolicies = sharePolicies.map(
                (policy) => {
                    let isActive = policy.enum === this.project.tileVisibility;
                    policy.active = isActive;
                    return policy;
                }
            );
            this.activePolicy = this.sharePolicies.find((policy) => policy.active);
        }
    }

    initShareURL() {
        if (this.project) {
            this.projectService.getProjectShareURL(this.project).then((url) => {
                this.shareUrl = url;
            });
        }
    }

    initTileURL() {
        if (this.project) {
            const tileUrl = this.projectService.getProjectLayerURL(this.project);
            let zxyUrl = tileUrl
                .replace('{z}', `{${urlMappings.standard.z}}`)
                .replace('{x}', `{${urlMappings.standard.x}}`)
                .replace('{y}', `{${urlMappings.standard.y}}`);
            let arcGISUrl = tileUrl
                .replace('{z}', `{${urlMappings.arcGIS.z}}`)
                .replace('{x}', `{${urlMappings.arcGIS.x}}`)
                .replace('{y}', `{${urlMappings.arcGIS.y}}`);
            if (this.activePolicy.enum !== 'PRIVATE') {
                this.tileLayerUrls = {
                    arcGIS: `${arcGISUrl}`,
                    standard: `${zxyUrl}`
                };
            } else {
                this.tokenService.getOrCreateProjectMapToken(this.project).then(
                    (mapToken) => {
                        this.mapToken = mapToken;
                        this.tileLayerUrls = {
                            arcGIS: `${arcGISUrl}&mapToken=${mapToken.id}`,
                            standard: `${zxyUrl}&mapToken=${mapToken.id}`
                        };
                    });
            }
        }
    }

    onPolicyChange(policy) {
        if (this.project) {
            let shouldUpdate = true;
            let lastPolicy = this.activePolicy;

            if (lastPolicy) {
                lastPolicy.active = false;
            } else {
                shouldUpdate = false;
            }

            this.activePolicy = policy;
            this.activePolicy.active = true;
            this.project.tileVisibility = this.activePolicy.enum;
            this.project.visibility = this.activePolicy.enum;

            if (shouldUpdate) {
                this.projectService.updateProject(this.project).then((res) => {
                    this.$log.debug(res);
                }, (err) => {
                    this.$log.debug('Error while updating project share policy', err);
                    this.activePolicy.active = false;
                    this.activePolicy = lastPolicy;
                    this.activePolicy.active = true;
                });
            }

            this.initTileURL();
            this.initShareURL();
        }
    }

    onSharePageOpen() {
        if (this.project) {
            this.$window.open(this.shareUrl, '_blank');
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

const SharingModule = angular.module('page.projects.edit.sharing', []);

SharingModule.controller('SharingController', SharingController);

export default SharingModule;
