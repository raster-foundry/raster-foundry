/* global BUILDCONFIG */

import angular from 'angular';
let assetLogo = BUILDCONFIG.LOGOFILE ?
    require(`../../../../assets/images/${BUILDCONFIG.LOGOFILE}`) :
    require('../../../../assets/images/raster-foundry-logo.svg');

assetLogo = BUILDCONFIG.LOGOURL || assetLogo;

class PlatformController {
    constructor(
        $stateParams,
        platformService, authService,
        platform, members, organizations,
        projects, rasters, vectors, datasources, templates, analyses
    ) {
        'ngInject';
        this.$stateParams = $stateParams;
        this.platformService = platformService;
        this.authService = authService;
        this.platformLogo = assetLogo;
        this.platform = platform;
        this.organizations = organizations;
        this.members = members;
        this.projects = projects;
        this.rasters = rasters;
        this.vectors = vectors;
        this.datasources = datasources;
        this.templates = templates;
        this.analyses = analyses;
    }

    $onInit() {
        this.isEffectiveAdmin = this.authService.isEffectiveAdmin(this.platform.id);
    }
}

const PlatformModule = angular.module('pages.platform', []);

PlatformModule.resolve = {
    platform: ($stateParams, platformService) => {
        return platformService.getPlatform($stateParams.platformId);
    },
    user: (authService) => {
        return authService.getCurrentUser();
    },
    members: (platform, platformService) => {
        return platformService.getMembers(platform.id, 0, '');
    },
    organizations: (platform, platformService) => {
        return platformService.getOrganizations(platform.id, 0, '');
    },
    projects: (platform, projectService) => {
        return projectService.query(
            {
                sort: 'createdAt,desc',
                pageSize: 10,
                page: 1,
                ownershipType: 'inherited',
                groupType: 'platform',
                groupId: platform.id
            }
        );
    },
    rasters: (platform, sceneService) => {
        return sceneService.query(
            {
                sort: 'createdAt,desc',
                pageSize: 10,
                page: 1,
                ownershipType: 'inherited',
                groupType: 'platform',
                groupId: platform.id
            }
        );
    },
    vectors: (platform, shapesService) => {
        return shapesService.query(
            {
                sort: 'createdAt,desc',
                pageSize: 10,
                page: 1,
                ownershipType: 'inherited',
                groupType: 'platform',
                groupId: platform.id
            }
        );
    },
    datasources: (platform, datasourceService) => {
        return datasourceService.query(
            {
                sort: 'createdAt,desc',
                pageSize: 10,
                page: 1,
                ownershipType: 'inherited',
                groupType: 'platform',
                groupId: platform.id
            }
        );
    },
    templates: (platform, analysisService) => {
        return analysisService.fetchTemplates(
            {
                sort: 'createdAt,desc',
                pageSize: 10,
                page: 1,
                ownershipType: 'inherited',
                groupType: 'platform',
                groupId: platform.id
            }
        );
    },
    analyses: (platform, analysisService) => {
        return analysisService.fetchAnalyses(
            {
                sort: 'createdAt,desc',
                pageSize: 10,
                page: 1,
                ownershipType: 'inherited',
                groupType: 'platform',
                groupId: platform.id
            }
        );
    }
};

PlatformModule.controller('PlatformController', PlatformController);

export default PlatformModule;
