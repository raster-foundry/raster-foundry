import angular from 'angular';
import _ from 'lodash';

import colorSchemeDropdownTpl from './colorSchemeDropdown.html';

const ColorSchemeDropdownComponent = {
    templateUrl: colorSchemeDropdownTpl,
    controller: 'ColorSchemeDropdownController',
    bindings: {
        colorSchemeOptions: '<',
        onChange: '&',
        disabled: '<'
    },
    controllerAs: '$ctrl'
};

const STATES = ['MAIN', 'SCHEME', 'BLENDING'];
const MIN_BINS = 2;
const MAX_BINS = 12;
const ELASTIC_NAV = true;

class ColorSchemeDropdownController {
    constructor($scope, $element, colorSchemeService) {
        'ngInject';
        this.$scope = $scope;
        this.$element = $element;
        this.colorSchemeService = colorSchemeService;

        this.bins = [0, ...[ ...Array(1 + MAX_BINS - MIN_BINS).keys()].map(b => b + MIN_BINS)];

        this.filterToValidSchemes = (value) => {
            const schemeValue = _.get(this, 'state.schemeType.value');
            const bins = _.get(this, 'state.blending.bins', 0);
            return value.type === schemeValue &&
                (bins === 0 ||
                 Object.keys(value).length >= bins);
        };
    }

    $onInit() {
        this.$element.on('wheel', (event) => {
            event.stopPropagation();
        });
    }

    $onChanges() {
        this.deserializeColorSchemeOptions();
        this.mergeStates();
    }

    deserializeColorSchemeOptions() {
        this._colorSchemeOptions = angular.fromJson(this.colorSchemeOptions);
    }

    mergeStates() {
        this.state = Object.assign(
            {},
            this.getInitialState(),
            this.getStateFromColorSchemeOptions()
        );
        this.reflectedState = Object.assign(
            {},
            this.getInitialState(),
            this.getStateFromColorSchemeOptions()
        );
    }

    getInitialState() {
        return {
            view: 'MAIN',
            blending: {
                label: this.getBlendingLabel(0),
                bins: 0
            },
            schemeType: {
                label: 'Sequential',
                value: 'SEQUENTIAL'
            },
            scheme: this.colorSchemeService.defaultColorSchemes.find(s => s.type === 'SEQUENTIAL'),
            reversed: false
        };
    }

    getStateFromColorSchemeOptions() {
        let stateToReturn = {};
        if (this._colorSchemeOptions && this._colorSchemeOptions.colorScheme) {
            const scheme = this.colorSchemeService.matchSingleBandOptions(
                this._colorSchemeOptions
            );

            if (scheme) {
                stateToReturn.scheme = scheme;
            }

            if (this._colorSchemeOptions.dataType) {
                stateToReturn.schemeType = this.colorSchemeService.defaultColorSchemeTypes.find(
                    t => this._colorSchemeOptions.dataType === t.value
                );
            }

            let blending = {};

            if (Array.isArray(this._colorSchemeOptions.colorScheme)) {
                let hasBins = Number.isFinite(this._colorSchemeOptions.colorBins);
                let colorSchemeBins = this._colorSchemeOptions.colorScheme.length;
                let bins = hasBins ? this._colorSchemeOptions.colorBins : 0;
                if (bins > colorSchemeBins) {
                    bins = colorSchemeBins;
                }
                blending = {
                    label: this.getBlendingLabel(bins),
                    bins: bins
                };
            } else {
                let bins = Object.keys(this._colorSchemeOptions.colorScheme).length;
                blending = {
                    label: this.getBlendingLabel(bins),
                    bins
                };
            }
            stateToReturn.blending = blending;
            stateToReturn.reversed = this._colorSchemeOptions.reversed;
        }
        return stateToReturn;
    }

    getColorSchemeOptionsFromState() {
        // @TODO: need to determine way forward for setting bin values?
        if (this.state) {
            return {
                colorScheme: this.colorSchemeService.colorStopsToProportionalArray(
                    this.state.scheme.colors, this.state.reversed
                ),
                dataType: this.state.schemeType.value,
                colorBins: this.state.blending.bins,
                reversed: this.state.reversed
            };
        }
        return {};
    }

    moveToView(view) {
        if (STATES.includes(view)) {
            this.state = Object.assign(
                {},
                this.state,
                {
                    view: view
                }
            );
        }
    }

    setSchemeType(schemeType) {
        if (schemeType.value === 'CATEGORICAL' && this.state.blending.bins === 0) {
            this.state = Object.assign(
                {},
                this.state,
                {
                    blending: {
                        label: this.getBlendingLabel(2),
                        bins: 2
                    }
                },
                { schemeType }
            );
        } else if (schemeType.value !== 'CATEGORICAL' && this.state.blending.bins > 0) {
            this.state = Object.assign(
                {},
                this.state,
                {
                    blending: {
                        label: this.getBlendingLabel(0),
                        bins: 0
                    }
                },
                { schemeType }
            );
        } else {
            this.state = Object.assign(
                {},
                this.state,
                { schemeType }
            );
        }
        if (ELASTIC_NAV) {
            this.moveToView('MAIN');
        }
    }

    setBlending(bins) {
        this.state = Object.assign(
            {},
            this.state,
            {
                blending: {
                    label: this.getBlendingLabel(bins),
                    bins
                }
            }
        );
        if (ELASTIC_NAV) {
            this.moveToView('MAIN');
        }
        if (this.state.schemeType.value === this.reflectedState.schemeType.value) {
            this.reflectState();
        }
    }

    setScheme(scheme) {
        this.state = Object.assign(
            {},
            this.state,
            { scheme }
        );
        this.reflectState();
    }

    reflectState() {
        if (this.onChange) {
            this.onChange({
                value: this.getColorSchemeOptionsFromState()
            });
        }
    }

    getSchemeClass(scheme) {
        if (this.state) {
            return {
                selected:
                _.isEqual(
                    this.state.scheme.colors,
                    scheme.colors
                )
            };
        }
        return {};
    }

    getSchemeTypeClass(schemeType) {
        return {
            selected: this.isActiveSchemeType(schemeType)
        };
    }

    getBlendingClass(bin) {
        if (this.state) {
            return {
                selected: this.state.blending.bins === bin
            };
        }
        return {};
    }

    getBlendingLabel(bin) {
        if (bin === 0) {
            return 'Continuous';
        }
        return `${bin} discrete bins`;
    }

    isActiveSchemeType(schemeType) {
        const schemeValue = _.get(this, 'state.schemeType.value');
        if (schemeValue) {
            return schemeValue === schemeType.value;
        }
        return false;
    }

    shouldShowView(view) {
        if (this.state) {
            return this.state.view === view;
        }
        return false;
    }

    onDropdownToggle(open) {
        if (!open) {
            this.mergeStates();
        }
    }

    reverseColors() {
        this.state.reversed = !this.state.reversed;
        this.reflectState();
    }
}

const ColorSchemeDropdownModule = angular.module('components.colorSchemeDropdown', []);

ColorSchemeDropdownModule.component(
    'rfColorSchemeDropdown',
    ColorSchemeDropdownComponent
);

ColorSchemeDropdownModule.controller(
    'ColorSchemeDropdownController',
    ColorSchemeDropdownController
);

export default ColorSchemeDropdownModule;
