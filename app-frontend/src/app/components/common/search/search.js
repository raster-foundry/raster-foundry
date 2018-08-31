/* global _ */
import searchTemplate from './search.html';

const searchComponent = {
    templateUrl: searchTemplate,
    controller: 'SearchController',
    bindings: {
        autoFocus: '<',
        disabled: '<',
        placeholder: '@',
        onSearch: '&',
        suggestions: '<',
        onSuggestionSelect: '&',
        showSuggestionAvatars: '<',
        value: '<?'
    }
};

class SearchController {
    constructor($rootScope, $scope, $element, $timeout) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        this.showSuggestions = true;
    }

    $postLink() {
        if (this.value) {
            this.searchText = this.value;
        }
        if (this.autoFocus) {
            this.claimFocus();
        }
        this.addBlurHandlers();
    }

    $onChanges(changes) {
        if (_.get(changes, 'disabled.currentValue')) {
            this.claimFocus(100);
        }
        if (_.get(changes, 'suggestions.currentValue') && !this.searchText) {
            this.suggestions = [];
        }
        if (changes.value && changes.value.currentValue !== this.searchText) {
            this.searchText = changes.currentValue;
        }
    }

    $onDestroy() {
        $(this.$element[0]).off();
    }

    claimFocus(interval = 0) {
        this.$timeout(() => {
            const el = $(this.$element[0]).find('input').get(0);
            if (el) {
                el.focus();
            }
        }, interval);
    }

    addBlurHandlers() {
        this.$timeout(() => {
            const el = $(this.$element[0]);
            el.on('focusin', () => {
                this.$timeout(() => {
                    this.showSuggestions = true;
                }, 0);
            });
            el.on('focusout', () => {
                this.$timeout(() => {
                    this.showSuggestions = false;
                }, 250);
            });
        }, 0);
    }

    clearSearch() {
        this.searchText = '';
        this.onSearch({value: null});
    }

    handleSuggestionSelect(suggestion) {
        this.showSuggestions = false;
        this.onSuggestionSelect({ value: suggestion });
    }
}

export default angular.module('components.common.search', [])
    .component('rfSearch', searchComponent)
    .controller('SearchController', SearchController);
