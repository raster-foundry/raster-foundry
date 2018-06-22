import angular from 'angular';

class OrganizationController {
    constructor(
        $stateParams, $q, $window,
        organizationService, authService, modalService,
        organization, platform, user, userRoles, members, teams,
        projects, rasters, vectors, datasources, templates, analyses
    ) {
        'ngInject';

        this.$stateParams = $stateParams;
        this.$q = $q;
        this.$window = $window;
        this.organizationService = organizationService;
        this.authService = authService;
        this.modalService = modalService;
        this.organization = organization;
        this.platform = platform;
        this.user = user;
        this.userRoles = userRoles;
        this.members = members;
        this.teams = teams;
        this.projects = projects;
        this.rasters = rasters;
        this.vectors = vectors;
        this.datasources = datasources;
        this.templates = templates;
        this.analyses = analyses;
    }

    $onInit() {
        this.isEffectiveAdmin = this.authService.isEffectiveAdmin([
            this.organization.id,
            this.platform.id
        ]);
    }

    addLogoModal() {
        this.modalService.open({
            component: 'rfAddPhotoModal',
            resolve: {
                organizationId: () => this.$stateParams.organizationId
            }
        }).result.then((resp) => {
            this.organization = Object.assign({}, this.organization, resp);
            this.logoUpdateTrigger = new Date().getTime();
        });
    }

    toggleOrgNameEdit() {
        this.isEditOrgName = !this.isEditOrgName;
    }

    finishOrgNameEdit() {
        if (
            this.nameBuffer &&
            this.nameBuffer.length &&
            this.nameBuffer !== this.organization.name
        ) {
            const orgUpdated = Object.assign({}, this.organization, {name: this.nameBuffer});

            this.organizationService.updateOrganization(
                this.organization.platformId, this.organization.id, orgUpdated
            ).then(resp => {
                this.organization = resp;
                this.nameBuffer = this.organization.name;
            }, () => {
                this.$window.alert('Organization\'s name cannot be updated at the moment.');
                delete this.nameBuffer;
            }).finally(() => {
                delete this.isEditOrgName;
            });
        } else {
            delete this.nameBuffer;
            delete this.isEditOrgName;
        }
    }
}

const OrganizationModule = angular.module('pages.organization', []);

OrganizationModule.resolve = {
    organization: ($stateParams, organizationService) => {
        return organizationService.getOrganization($stateParams.organizationId);
    },
    platform: (organization, platformService) => {
        return platformService.getPlatform(organization.platformId);
    },
    user: (authService) => {
        return authService.getCurrentUser();
    },
    members: (platform, organization, organizationService) => {
        return organizationService.getMembers(platform.id, organization.id, 0, '');
    },
    teams: (platform, organization, organizationService) => {
        return organizationService.getTeams(platform.id, organization.id, 0, '');
    },
    userRoles: (authService) => {
        return authService.fetchUserRoles();
    },
    projects: (organization, projectService) => {
        return projectService.query(
            {
                sort: 'createdAt,desc',
                pageSize: 10,
                page: 1,
                ownershipType: 'inherited',
                groupType: 'organization',
                groupId: organization.id
            }
        );
    },
    rasters: (organization, sceneService) => {
        return sceneService.query(
            {
                sort: 'createdAt,desc',
                pageSize: 10,
                page: 1,
                ownershipType: 'inherited',
                groupType: 'organization',
                groupId: organization.id
            }
        );
    },
    vectors: (organization, shapesService) => {
        return shapesService.query(
            {
                sort: 'createdAt,desc',
                pageSize: 10,
                page: 1,
                ownershipType: 'inherited',
                groupType: 'organization',
                groupId: organization.id
            }
        );
    },
    datasources: (organization, datasourceService) => {
        return datasourceService.query(
            {
                sort: 'createdAt,desc',
                pageSize: 10,
                page: 1,
                ownershipType: 'inherited',
                groupType: 'organization',
                groupId: organization.id
            }
        );
    },
    templates: (organization, analysisService) => {
        return analysisService.fetchTemplates(
            {
                sort: 'createdAt,desc',
                pageSize: 10,
                page: 1,
                ownershipType: 'inherited',
                groupType: 'organization',
                groupId: organization.id
            }
        );
    },
    analyses: (organization, analysisService) => {
        return analysisService.fetchAnalyses(
            {
                sort: 'createdAt,desc',
                pageSize: 10,
                page: 1,
                ownershipType: 'inherited',
                groupType: 'organization',
                groupId: organization.id
            }
        );
    }
};

OrganizationModule.controller('OrganizationController', OrganizationController);

export default OrganizationModule;
