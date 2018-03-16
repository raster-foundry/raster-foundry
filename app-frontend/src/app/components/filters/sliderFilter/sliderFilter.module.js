import angular from 'angular';
import sliderFilterTpl from './sliderFilter.html';

const SliderFilterComponent = {
    templateUrl: sliderFilterTpl,
    controller: 'SliderFilterController',
    bindings: {
        filter: '<',
        onFilterChange: '&'
    }
};

class SliderFilterController {
    constructor($location) {
        this.$location = $location;
    }

    $onChanges(changes) {
        if (changes.filter && changes.filter.currentValue) {
            this.filter = changes.filter.currentValue;

            const urlParams = this.$location.search();
            const minParam = parseFloat(urlParams[this.filter.params.min]);
            const maxParam = parseFloat(urlParams[this.filter.params.max]);
            let min = this.filter.min;
            let max = this.filter.max;

            if (Number.isFinite(minParam)) {
                min = minParam / this.filter.scale;
            }
            if (Number.isFinite(maxParam)) {
                max = maxParam / this.filter.scale;
            }

            this.filterData = {
                minModel: min,
                maxModel: max
            };

            this.options = {
                floor: this.filter.min,
                ceil: this.filter.max,
                minRange: 0,
                showTicks: this.filter.ticks,
                showTicksValues: true,
                step: this.filter.step,
                pushRange: true,
                draggableRange: true,
                onEnd: (id, minModel, maxModel) => {
                    this.setFilter(minModel, maxModel);
                }
            };

            this.setFilter(min, max);
        }
    }

    setFilter(min, max) {
        const filterParams = {};
        if (min !== this.filter.min) {
            filterParams[this.filter.params.min] = min * this.filter.scale;
        } else {
            filterParams[this.filter.params.min] = null;
        }
        if (max !== this.filter.max) {
            filterParams[this.filter.params.max] = max * this.filter.scale;
        } else {
            filterParams[this.filter.params.max] = null;
        }
        this.onFilterChange({filter: this.filter, filterParams});
    }
}

const SliderFilterModule = angular.module('components.filters.sliderFilter', []);

SliderFilterModule.component('rfSliderFilter', SliderFilterComponent);
SliderFilterModule.controller('SliderFilterController', SliderFilterController);

export default SliderFilterModule;
