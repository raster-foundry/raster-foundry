import tpl from './index.html';

class AnalysesListController {
    constructor(
        $rootScope, $scope, $state, $log,
        analysisService
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        this.fetchPage();
    }

    fetchPage(page = this.$state.params.page || 1) {
        this.analysisList = [];
        const currentQuery = this.analysisService.fetchAnalyses(
            {
                pageSize: 30,
                page: page - 1,
                projectId: this.project.id
            }
        ).then((paginatedAnalyses) => {
            this.analysisList = paginatedAnalyses.results;
            this.pagination = this.paginationService.buildPagination(paginatedAnalyses);
            this.paginationService.updatePageParam(page);
            if (this.currentQuery === currentQuery) {
                delete this.fetchError;
            }
        }).catch((e) => {
            if (this.currentQuery === currentQuery) {
                this.fetchError = e;
            }
        }).finally(() => {
            if (this.currentQuery === currentQuery) {
                delete this.currentQuery;
            }
        });
        this.currentQuery = currentQuery;
        return currentQuery;
    }
}

const component = {
    bindings: {
        project: '<'
    },
    templateUrl: tpl,
    controller: AnalysesListController.name
};

export default angular
    .module('components.pages.project.analyses.page', [])
    .controller(AnalysesListController.name, AnalysesListController)
    .component('rfProjectAnalysesPage', component)
    .name;
