/* global BUILDCONFIG, HELPCONFIG */
import pagination from 'angular-ui-bootstrap/src/pagination';

class ProjectsListController {
    constructor( // eslint-disable-line max-params
        $log, $state, modalService, $scope,
        paginationService, projectService, userService, authService, platform, user
    ) {
        'ngInject';
        $scope.autoInject(this, arguments);
    }

    $onInit() {
        this.BUILDCONFIG = BUILDCONFIG;
        this.HELPCONFIG = HELPCONFIG;
        // Can be one of {owned, shared}
        this.currentOwnershipFilter = this.$state.params.ownership || 'owned';
        this.fetchPage();
    }

    fetchPage(page = this.$state.params.page || 1, search = this.$state.params.search) {
        this.search = search && search.length ? search : null;
        delete this.fetchError;
        this.results = [];
        let currentQuery = this.projectService.query({
            sort: 'createdAt,desc',
            pageSize: 10,
            page: page - 1,
            ownershipType: this.currentOwnershipFilter,
            search: this.search
        }).then(paginatedResponse => {
            this.results = paginatedResponse.results;
            this.results.forEach((project) => {
                this.getProjectScenesCount(project);
            });
            this.pagination = this.paginationService.buildPagination(paginatedResponse);
            this.paginationService.updatePageParam(page, this.search, null, {
                ownership: this.currentOwnershipFilter
            });
            if (this.currentQuery === currentQuery) {
                delete this.fetchError;
            }
        }, (e) => {
            if (this.currentQuery === currentQuery) {
                this.fetchError = e;
            }
        }).finally(() => {
            if (this.currentQuery === currentQuery) {
                delete this.currentQuery;
            }
        });
        this.currentQuery = currentQuery;
    }

    shouldShowPlaceholder() {
        return !this.currentQuery &&
            !this.fetchError &&
            (!this.search || !this.search.length) &&
            this.pagination &&
            this.pagination.count === 0;
    }

    getProjectScenesCount(project) {
        this.projectService.getProjectSceneCount({projectId: project.id}).then(
            (sceneResult) => {
                let bupdate = this.results.find((b) => b.id === project.id);
                bupdate.scenes = sceneResult.count;
            }
        );
    }

    viewProjectDetail(project) {
        this.$state.go('^.detail', {project: project, projectid: project.id});
    }

    createNewProject() {
        const modal = this.modalService.open({
            component: 'rfProjectCreateModal'
        });

        modal.result.then((data) => {
            if (data && data.reloadProjectList) {
                this.fetchPage(1);
            }
        }).catch(() => {});
    }

    handleOwnershipFilterChange(newFilterValue) {
        this.fetchPage(1);
    }
}

const ProjectsListModule = angular.module('pages.projects.list', [pagination]);

ProjectsListModule.controller('ProjectsListController', ProjectsListController);

export default ProjectsListModule;
