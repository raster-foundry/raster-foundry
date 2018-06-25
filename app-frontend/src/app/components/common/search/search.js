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
    constructor($element, $timeout) {
        'ngInject';
        this.$element = $element;
        this.$timeout = $timeout;
    }

    $postLink() {
        if (this.autoFocus) {
            this.claimFocus();
        }
    }

    $onChanges(changes) {
        if (changes.disabled.currentValue) {
            this.claimFocus(100);
        }
    }

    claimFocus(interval = 0) {
        this.$timeout(() => {
            const el = $(this.$element[0]).find('input').get(0);
            el.focus();
        }, interval);
    }

    clearSearch() {
        this.searchText = '';
        this.onSearch();
    }
}

export default angular.module('components.common.search', [])
    .component('rfSearch', searchComponent)
    .controller('SearchController', SearchController);
