export default class ProjectPublishModalController {
    constructor($q, projectService, $log, tokenService, authService, $uibModal, $window, $state) {
        'ngInject';

        this.authService = authService;
        this.projectService = projectService;
        this.$log = $log;
        this.tokenService = tokenService;
        this.$uibModal = $uibModal;
        this.$q = $q;
        this.$window = $window;
        this.$state = $state;
    }

    $onInit() {
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

        this.project = this.resolve.project;

        this.sharePolicies = sharePolicies.map(
            (policy) => {
                let isActive = policy.enum === this.resolve.project.tileVisibility;
                policy.active = isActive;
                return policy;
            }
        );

        this.activePolicy = this.sharePolicies.find((policy) => policy.active);
        this.tileLayerUrls = {
            standard: null,
            arcGIS: null
        };
        this.updateShareUrl();
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
        if (shouldUpdate) {
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

    openProjectShare() {
        let url = this.$state.href('share', {projectid: this.resolve.project.id});
        this.$window.open(url, '_blank');
    }

    hydrateTileUrls() {
        let zxyUrl = this.resolve.tileUrl
            .replace('{z}', `{${this.urlMappings.standard.z}}`)
            .replace('{x}', `{${this.urlMappings.standard.x}}`)
            .replace('{y}', `{${this.urlMappings.standard.y}}`);
        let arcGISUrl = this.resolve.tileUrl
            .replace('{z}', `{${this.urlMappings.arcGIS.z}}`)
            .replace('{x}', `{${this.urlMappings.arcGIS.x}}`)
            .replace('{y}', `{${this.urlMappings.arcGIS.y}}`);
        if (this.activePolicy.enum !== 'PRIVATE') {
            this.tileLayerUrls.arcGIS = `${arcGISUrl}`;
            this.tileLayerUrls.standard = `${zxyUrl}`;
        } else {
            this.tokenService.getOrCreateProjectMapToken(this.project).then(
                (mapToken) => {
                    this.mapToken = mapToken;
                    this.tileLayerUrls.standard = `${zxyUrl}&mapToken=${mapToken.id}`;
                    this.tileLayerUrls.arcGIS = `${arcGISUrl}&mapToken=${mapToken.id}`;
                });
        }
    }
}
