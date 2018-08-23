/* global BUILDCONFIG */
class VectorListController {
    constructor($scope, $state, paginationService, authService, modalService, shapesService) {
        'ngInject';
        $scope.autoInject(this, arguments);
        this.BUILDCONFIG = BUILDCONFIG;
    }

    $onInit() {
        this.fetchPage();
    }

    shouldShowShapeList() {
        return !this.loading && this.lastShapeResult &&
            this.lastShapeResult.count > this.lastShapeResult.pageSize && !this.errorMsg;
    }

    shouldShowImportBox() {
        return !this.loading && this.lastShapeResult &&
            this.lastShapeResult.count === 0 && !this.errorMsg;
    }

    fetchPage(page = this.$state.params.page || 1, search = this.$state.params.search) {
        this.search = search && search.length ? search : null;
        delete this.fetchError;
        this.results = [];
        const currentQuery = this.shapesService.fetchShapes({
            page: page ? page - 1 : 0,
            pageSize: 10,
            search: this.search
        }).then((paginatedResponse) => {
            this.results = paginatedResponse.features;
            this.pagination = this.paginationService.buildPagination(paginatedResponse);
            this.paginationService.updatePageParam(page, this.search);
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

    deleteShape(shape) {
        this.shapesService.deleteShape({id: shape.id}).then(() => {
            this.fetchPage();
        });
    }

    importModal() {
        let modal = this.modalService.open({
            component: 'rfVectorImportModal',
            resolve: {}
        });
        modal.result.then(() => {
            this.fetchPage();
        });
    }
}

const VectorListModule = angular.module('pages.imports.vectors', []);

VectorListModule.controller('VectorListController', VectorListController);

export default VectorListModule;
