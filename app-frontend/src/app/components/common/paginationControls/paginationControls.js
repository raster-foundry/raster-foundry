import tpl from './paginationControls.html';

const PaginationControlsComponent = {
    bindings: {
        pagination: '=',
        isLoading: '<',
        onChange: '&'
    },
    templateUrl: tpl,
    controller: 'PaginationController'
};

class Controller {
    handleOnChange(page) {
        this.onChange({ value: page });
    }
}

export default angular
    .module('components.paginationControls', [])
    .controller('PaginationController', Controller)
    .component('rfPaginationControls', PaginationControlsComponent);
