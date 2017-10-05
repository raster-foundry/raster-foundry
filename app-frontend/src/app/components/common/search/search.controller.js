export default class SearchController {
    constructor( // eslint-disable-line max-params
        $log, $state
    ) {
        'ngInject';

        this.$log = $log;
        this.$state = $state;
    }

    onSearchAction() {
        this.onSearch({value: this.searchText});
    }

    clearSearch() {
        this.searchText = '';
    }
}
