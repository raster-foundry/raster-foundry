// rfToolSearch controller class
export default class ToolSearchController {
    constructor( // eslint-disable-line max-params
        $log, $state
    ) {
        'ngInject';

        this.$log = $log;
        this.$state = $state;
    }

    onSearchAction() {
        this.onSearch({text: this.searchText});
    }

    clearSearch() {
        this.searchText = '';
    }
}
