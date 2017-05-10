const updateFrequencies = [
    {
        label: 'every 6 hours'
    },
    {
        label: 'every 12 hours'
    },
    {
        label: 'every day'
    },
    {
        label: 'every week'
    },
    {
        label: 'every two weeks'
    },
    {
        label: 'every month'
    }
];

export default class AOIParametersController {
    constructor() {
        'ngInject';
    }

    $onInit() {
        this.updateFrequencies = updateFrequencies;
        this.showFilters = false;
        this.filters = {};
    }

    toggleFilters() {
        this.showFilters = !this.showFilters;
    }
}
