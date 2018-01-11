import angular from 'angular';
import _ from 'lodash';
import tagFilterTpl from './tagFilter.html';

const tagFilterComponent = {
    templateUrl: tagFilterTpl,
    controller: 'TagFilterController',
    bindings: {
        filter: '<',
        onFilterChange: '&'
    }
};

class TagFilterController {
    constructor($location) {
        this.$location = $location;
        this.selectedOption = {value: null};
    }

    $onChanges(changes) {
        if (changes.filter && changes.filter.currentValue) {
            this.filter = changes.filter.currentValue;
            let paramValue = this.$location.search()[this.filter.param];
            let option = _.first(
                this.filter.options.filter(o => o.value === paramValue)
            );
            if (!option) {
                option = _.first(
                    this.filter.options.filter(o => o.value === null)
                );
            }
            this.selectOption(option);
        }
    }

    selectOption(option) {
        this.selectedOption = option;
        const filterParams = {};
        filterParams[this.filter.param] = option.value;
        this.onFilterChange({filter: this.filter, filterParams});
    }

}

const TagFilterModule = angular.module('components.filters.tagFilter', []);

TagFilterModule.component('rfTagFilter', tagFilterComponent);
TagFilterModule.controller('TagFilterController', TagFilterController);

export default TagFilterModule;
