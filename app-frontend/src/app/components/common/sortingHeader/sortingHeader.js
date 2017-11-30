import sortingHeaderTemplate from './sortingHeader.html';

const sortingHeaderComponent = {
    templateUrl: sortingHeaderTemplate,
    controller: 'SortingHeaderController',
    bindings: {
        direction: '<',
        isActive: '<',
        onSortChange: '&'
    },
    transclude: true
};

class SortingHeaderController {
    constructor() {
        'ngInject';
    }

    getIconClass() {
        return this.direction === 'asc' ? 'icon-caret-up' : 'icon-caret-down';
    }
}

export default angular.module('components.common.sortingHeader', [])
    .component('rfSortingHeader', sortingHeaderComponent)
    .controller('SortingHeaderController', SortingHeaderController);
