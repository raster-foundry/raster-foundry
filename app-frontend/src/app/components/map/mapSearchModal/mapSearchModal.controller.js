/* eslint-disable */

const UPARROW = 38;
const DOWNARROW = 40;
const TAB = 9;
const ENTER = 13;

export default class MapSearchModalController {

    constructor($scope, $state, $element, $timeout, geocodeService) {
        'ngInject';
        this.$scope = $scope;
        this.$state = $state;
        this.$element = $element;
        this.$timeout = $timeout;
        this.geocodeService = geocodeService;
    }

    $onInit() {
        this.isLoading = false;
        this.isError = false;
        this.query = '';
        this.activeResultIndex = -1;

    }

    $postLink() {
        this.$timeout(() => {
            const el = $(this.$element[0]).find('input').get(0);
            el.focus();
            $(el).on('keydown', $.proxy(this.handleKeypress, this));
        }, 0);
    }

    handleKeypress(e) {
        if (e.which === UPARROW) {
            e.preventDefault();
            this.incrementActiveResultIndex(-1);
        } else if (e.which === DOWNARROW || e.which === TAB) {
            e.preventDefault();
            this.incrementActiveResultIndex(1);
        } else if (e.which === ENTER) {
            e.preventDefault();
            this.gotoActiveResult();
        }
    }

    incrementActiveResultIndex(i) {
        if (this.results) {
            const numResults = this.results.suggestions.length;
            this.$scope.$evalAsync(() => {
                this.activeResultIndex += i;
                if (this.activeResultIndex >= numResults) {
                    this.activeResultIndex = 0;
                } else if (this.activeResultIndex < 0) {
                    this.activeResultIndex = numResults - 1;
                }
            });
        }
    }

    selectLocation(i) {
        this.activeResultIndex = i;
        this.gotoActiveResult();
    }

    gotoActiveResult() {
        if (
            this.results &&
            this.results.suggestions &&
            this.results.suggestions.length > this.activeResultIndex &&
            this.activeResultIndex >= 0
        ) {
            const suggestion = this.results.suggestions[this.activeResultIndex];
            if (suggestion.coordinateFlag) {
                this.close({$value: suggestion});
            } else {
                const locationId = suggestion.locationId;
                this.geocodeService.getLocation(locationId).then(l => {
                    this.close({$value: l.response.view[0].result[0].location});
                });
            }
        }
    }

    search() {
        if (this.isCoordinatePair(this.query)) {
            this.results = {
                suggestions: [{
                    label: `Go to coordinates: ${this.query}`,
                    coordinateFlag: true,
                    coords: this.extractCoordinatePair(this.query)
                }]
            };
        } else {
            this.isLoading = true;
            this.geocodeService.getLocationSuggestions(this.query).then(r => {
                this.$scope.$evalAsync(() => {
                    this.results = r;
                    this.isLoading = false;
                    this.activateFirstResult();
                });
            });
        }
    }

    activateFirstResult() {
        if (
            this.results &&
            this.results.suggestions &&
            this.results.suggestions.length
        ) {
            this.activeResultIndex = 0;
        } else {
            this.activeResultIndex = -1;
        }
    }

    isActiveResultIndex(i) {
        return i === this.activeResultIndex;
    }

    shouldShowSearchPrompt() {
        return !this.query;
    }

    shouldShowResults() {
        return this.query &&
            this.results &&
            this.results.suggestions &&
            this.results.suggestions.length;
    }

    shouldShowLoadingMessage() {
        return this.query &&
            this.isLoading &&
            !this.results;
    }

    shouldShowNoResultsMessage() {
        return this.query &&
            !this.isLoading &&
            this.results &&
            this.results.suggestions &&
            !this.results.suggestions.length;
    }

    isCoordinatePair(searchString) {
        const cleanedStrings = this.extractCoordinatePair(searchString);
        return cleanedStrings.length === 2 &&
            !isNaN(cleanedStrings[0]) &&
            !isNaN(cleanedStrings[1]) &&
            cleanedStrings[0].length &&
            cleanedStrings[1].length &&
            +cleanedStrings[0] >= -90 && +cleanedStrings[0] <= 90 &&
            +cleanedStrings[1] >= -180 && +cleanedStrings[1] <= 180 ;
    }

    extractCoordinatePair(searchString) {
        return searchString.replace(/ /g, '').split(',');
    }
}
