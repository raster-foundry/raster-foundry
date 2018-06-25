import tpl from './paginationCount.html';

const PaginationCountComponent = {
    bindings: {
        startIndex: '<',
        endIndex: '<',
        total: '<',
        itemName: '@'
    },
    templateUrl: tpl
};

export default angular
    .module('components.paginationCount', [])
    .component('rfPaginationCount', PaginationCountComponent);
