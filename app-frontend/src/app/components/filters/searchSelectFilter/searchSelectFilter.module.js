import angular from 'angular';
import _ from 'lodash';
import searchSelectFilterTpl from './searchSelectFilter.html';

const SearchSelectFilterComponent = {
    templateUrl: searchSelectFilterTpl,
    controller: 'SearchSelectFilterController',
    bindings: {
        filter: '<',
        onFilterChange: '&'
    }
};

class SearchSelectFilterController {
    constructor($scope, $location) {
        this.$scope = $scope;
        this.$location = $location;
        this.selectedOption = null;
    }

    $onChanges(changes) {
        if (changes.filter && changes.filter.currentValue) {
            this.filter = changes.filter.currentValue;
            const paramValue = this.$location.search()[this.filter.param];
            this.filter.getSources().then((sources) => {
                this.sources = sources;

                let paramSource = _.first(
                    this.sources.filter((source) => source.id === paramValue)
                ) || this.sources.find(s => s.default);
                this.selectOption(paramSource);
            }, (err) => {
                this.error = err;
            });
        }
    }

    selectOption(option) {
        this.selectedOption = option;

        const filterParams = {};
        filterParams[this.filter.param] = option && option.id ? option.id : null;
        this.onFilterChange({filter: this.filter, filterParams});
    }

    clearOption() {
        this.selectedOption = null;

        const filterParams = {};
        filterParams[this.filter.param] = null;
        this.onFilterChange({filter: this.filter, filterParams});
    }
}

const SearchSelectFilterModule = angular.module('components.filters.searchSelectFilter', []);

SearchSelectFilterModule.component('rfSearchSelectFilter', SearchSelectFilterComponent);
SearchSelectFilterModule.controller('SearchSelectFilterController', SearchSelectFilterController);

export default SearchSelectFilterModule;
