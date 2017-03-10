export default class PublishModalController {
    constructor(projectService, $log) {
        'ngInject';

        this.projectService = projectService;
        this.$log = $log;
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
                active: false
            },
            {
                label: 'Organization',
                description:
                `Users in your organization will be able to use
                 their own tokens to view tiles for this project`,
                enum: 'ORGANIZATION',
                active: false
            },
            {
                label: 'Public',
                description: 'Anyone can view tiles for this project without a token',
                enum: 'PUBLIC',
                active: false
            }
        ];

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
    }

    onPolicyChange(policy) {
        let project = this.resolve.project;
        let shouldUpdate = true;

        let oldPolicy = this.activePolicy;
        if (this.activePolicy) {
            this.activePolicy.active = false;
        } else {
            shouldUpdate = false;
        }

        this.activePolicy = policy;
        policy.active = true;

        project.tileVisibility = policy.enum;
        if (shouldUpdate) {
            this.projectService.updateProject(project).then((res) => {
                this.$log.log(res);
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
}
