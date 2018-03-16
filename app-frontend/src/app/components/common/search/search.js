import searchTemplate from './search.html';

const searchComponent = {
    templateUrl: searchTemplate,
    controller: 'SearchController',
    bindings: {
        autoFocus: '<',
        disabled: '<',
        placeholder: '@',
        onSearch: '&'
    }
};

class SearchController {
    constructor(
        $element, $timeout
    ) {
        'ngInject';
        this.$element = $element;
        this.$timeout = $timeout;
    }

    $postLink() {
        if (this.autoFocus) {
            this.$timeout(() => {
                const el = $(this.$element[0]).find('input').get(0);
                el.focus();
            }, 0);
        }
    }

    clearSearch() {
        this.searchText = '';
        this.onSearch();
    }
}

export default angular.module('components.common.search', [])
    .component('rfSearch', searchComponent)
    .controller('SearchController', SearchController);
