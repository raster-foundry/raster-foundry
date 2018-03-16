class VectorListController {
    constructor(authService, modalService, shapesService) {
        'ngInject';
        this.authService = authService;
        this.modalService = modalService;
        this.shapesService = shapesService;
    }

    $onInit() {
        this.getShapes(0);
    }

    $onDestroy() {

    }

    shouldShowShapeList() {
        return !this.loading && this.lastShapeResult &&
            this.lastShapeResult.count > this.lastShapeResult.pageSize && !this.errorMsg;
    }

    shouldShowImportBox() {
        return !this.loading && this.lastShapeResult &&
            this.lastShapeResult.count === 0 && !this.errorMsg;
    }

    getShapes(page) {
        this.loading = true;
        this.shapesService.fetchShapes({
            page: page ? page - 1 : 0, pageSize: 10
        }).then((response) => {
            this.loading = false;
            this.lastShapeResult = response;
            this.shapeList = response.features;
        }, () => {
            this.loading = false;
            this.errorMsg = 'There was an error fetching your shapes from the API.';
        });
    }

    deleteShape(shape) {
        this.shapesService.deleteShape({id: shape.id}).then(() => {
            this.getShapes(this.currentPage);
        });
    }

    importModal() {
        let modal = this.modalService.open({
            component: 'rfVectorImportModal',
            resolve: {}
        });
        modal.result.then(() => {
            this.getShapes();
        });
    }
}

const VectorListModule = angular.module('pages.imports.vectors', []);

VectorListModule.controller('VectorListController', VectorListController);

export default VectorListModule;
