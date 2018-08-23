import tpl from './paginationCount.html';

const PaginationCountComponent = {
    transclude: true,
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
