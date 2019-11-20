import angular from 'angular';
import ingestedFilterTpl from './ingestedFilter.html';

const IngestedFilterComponent = {
    templateUrl: ingestedFilterTpl,
    controller: 'IngestedFilterController',
    bindings: {
        filter: '<',
        onFilterChange: '&'
    }
};

class IngestedFilterController {
    constructor($location) {
        this.$location = $location;
    }

    $onChanges(changes) {
        if (changes.filter && changes.filter.currentValue) {
            const urlParams = this.$location.search();
            const ingested = !urlParams.ingested;

            this.filterData = { ingested };

            this.setFilter(ingested);
        }
    }

    onToggleFilter(value = !this.filterData.ingested) {
        this.filterData.ingested = value;
        this.setFilter(value);
    }

    setFilter(ingested) {
        let filterParams = {};
        filterParams.ingested = ingested;

        this.onFilterChange({
            filter: this.filter,
            filterParams
        });
    }
}

const IngestedFilterModule = angular.module('components.filters.checkboxFilter', []);

IngestedFilterModule.component('rfCheckboxFilter', IngestedFilterComponent);
IngestedFilterModule.controller('IngestedFilterController', IngestedFilterController);

export default IngestedFilterModule;
