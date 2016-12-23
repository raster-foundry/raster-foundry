export default class MarketController {
    constructor( // eslint-disable-line max-params
        $log, $state
    ) {
        'ngInject';
        this.$log = $log;
        this.$state = $state;
    }

    search(text) {
        this.$log.log('searched for:', text);
        this.$state.go('market.search', {query: text});
    }
}
