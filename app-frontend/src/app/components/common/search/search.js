import angular from 'angular';
import searchTpl from './search.html';

const searchComponent = {
    templateUrl: searchTpl,
    controller: 'SearchController',
    bindings: {
        autoFocus: '<',
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

    onSearchAction() {
        this.onSearch({value: this.searchText});
    }

    clearSearch() {
        this.searchText = '';
    }
}

export default angular.module('components.common.search', [])
    .component('rfSearch', searchComponent)
    .controller('SearchController', SearchController);
