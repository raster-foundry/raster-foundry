export default class SearchController {
    constructor( // eslint-disable-line max-params
        $log, $state, $element, $timeout
    ) {
        'ngInject';

        this.$log = $log;
        this.$state = $state;
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
