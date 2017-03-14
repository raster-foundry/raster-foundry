export default class PublishModalController {
    constructor(projectService, $log, tokenService, authService, $uibModal) {
        'ngInject';

        this.authService = authService;
        this.projectService = projectService;
        this.$log = $log;
        this.tokenService = tokenService;
        this.$uibModal = $uibModal;

        // TODO change this when we implement orgs
        this.userOrg = 'dfac6307-b5ef-43f7-beda-b9f208bb7726'; // default public org
    }

    $onInit() {
        this.urlMappings = [
            {
                label: 'Standard',
                z: 'z',
                x: 'x',
                y: 'y',
                active: true
            },
            {
                label: 'ArcGIS',
                z: 'level',
                x: 'col',
                y: 'row',
                active: false
            }
        ];

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

        this.mappedTileUrl =
            this.hydrateTileUrl(this.getActiveMapping());

        this.fetchTokens();
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
    }

    onUrlMappingChange(mapping) {
        this.setActiveMappingByLabel(mapping.label);
        this.mappedTileUrl = this.hydrateTileUrl(mapping);
    }

    hydrateTileUrl(mapping) {
        return this.resolve.tileUrl
            .replace('{z}', `{${mapping.z}}`)
            .replace('{x}', `{${mapping.x}}`)
            .replace('{y}', `{${mapping.y}}`);
    }

    getActiveMapping() {
        return this.urlMappings.find(m => m.active);
    }

    setActiveMappingByLabel(label) {
        this.urlMappings.forEach(m => {
            m.active = m.label === label;
        });
    }

    createMapToken(name) {
        this.tokenService.createMapToken({
            name: name,
            project: this.project.id,
            organizationId: this.userOrg
        }).then((res) => {
            // TODO: Toast this
            this.$log.debug('token created!', res);
            this.newTokenName = '';
            this.fetchTokens();
        }, (err) => {
            // TODO: Toast this
            this.$log.debug('error creating token', err);
            this.fetchTokens();
        });
    }

    fetchTokens() {
        this.loadingTokens = true;
        this.tokenService.queryMapTokens({project: this.project.id}).then(
            (paginatedResponse) => {
                delete this.error;
                this.tokens = paginatedResponse.results;
                this.loadingTokens = false;
            },
            (error) => {
                this.error = error;
                this.loadingTokens = false;
            });
    }

    deleteToken(token) {
        let deleteModal = this.$uibModal.open({
            component: 'rfConfirmationModal',
            resolve: {
                title: () => 'Delete map token?',
                content: () => 'Deleting this map token will make any ' +
                    'further requests with it fail',
                confirmText: () => 'Delete Map Token',
                cancelText: () => 'Cancel'
            }
        });
        deleteModal.result.then(
            () => {
                this.tokenService.deleteMapToken({id: token.id}).then(
                    () => {
                        this.fetchTokens();
                    },
                    (err) => {
                        this.$log.debug('error deleting map token', err);
                        this.fetchTokens();
                    }
                );
            });
    }

    updateToken(token, name) {
        let newToken = Object.assign({}, token, {name: name});
        this.tokenService.updateMapToken(newToken).then((res) => {
            // TODO: Toast this
            this.$log.debug('token updated', res);
            this.fetchTokens();
        }, (err) => {
            this.$log.debug('error updating token', err);
            this.fetchTokens();
        });
    }
}
