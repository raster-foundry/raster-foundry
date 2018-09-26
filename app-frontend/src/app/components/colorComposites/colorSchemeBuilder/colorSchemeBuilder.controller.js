/* global _ */
import {Set} from 'immutable';

export default class ColorSchemeBuilderController {
    constructor($scope) {
        'ngInject';
        this.$scope = $scope;
    }

    $onInit() {
        this.initBuffer(true);
    }

    $onChanges(changes) {
        if ('colorScheme' in changes) {
            this.initBuffer();
        }
    }

    initBuffer(sort = false) {
        const incomingSchemeAsArray = this.getSchemeAsArray(sort);

        // This ensures that we don't unecessarily overwrite our
        // buffer with a differently ordered but identical scheme
        // which would cause UI elements to jump around
        if (
            !this.buffer ||
            !_.isEqual(
                this.getArraySchemeAsComparable(incomingSchemeAsArray),
                this.getArraySchemeAsComparable(this.buffer)
            )
        ) {
            this.buffer = [ ...incomingSchemeAsArray ];
            // coerce color scheme to map, since it's initially set as an array in the dropdown
            this.onChange({
                value: {
                    schemeColors: this.getBufferAsObject(),
                    masked: this.maskedValues || []
                }
            });
        }
        this.isValid = true;
    }

    getArraySchemeAsComparable(scheme) {
        return [ ...scheme ]
            .sort((a, b) => a.break - b.break)
            .map(c => angular.toJson(c));
    }

    getSchemeAsArray(sort = false) {
        const scheme = Object.keys(this.colorScheme).map(b => {
            return {
                break: +b,
                color: this.colorScheme[b].length === 7 ?
                    this.colorScheme[b] :
                    this.colorScheme[b].substr(0, this.colorScheme[b].length - 2),
                masked: this.colorScheme[b].masked,
                errors: []
            };
        });

        if (sort) {
            scheme.sort((a, b) => a.break - b.break);
        }

        return scheme;
    }

    getBufferAsObject() {
        return this.buffer.reduce((acc, c) => {
            acc[c.break] = c.color;
            return acc;
        }, {});
    }

    onBufferChanged() {
        if (this.validateBuffer()) {
            this.isValid = true;
            this.applyBuffer();
        } else {
            this.isValid = false;
        }
    }

    applyBuffer() {
        this.onChange({
            value: {
                schemeColors: this.getBufferAsObject(),
                masked: this.maskedValues
            }
        });
    }

    onColorRemove(index) {
        this.buffer = [
            ...this.buffer.slice(0, index),
            ...this.buffer.slice(index + 1, this.buffer.length)
        ];
        this.onBufferChanged();
    }

    onColorAdd(index, colorBreak) {
        const previousColor = this.buffer[index];
        this.buffer = [
            ...this.buffer.slice(0, index + 1),
            {
                color: previousColor.color,
                break: colorBreak + 1
            },
            ...this.buffer.slice(index + 1, this.buffer.length)
        ];
        this.onBufferChanged();
    }

    clearBufferValidation() {
        // When field-level validation and messages are implemented this will
        // need to be more complex
        this.isValid = false;
    }

    validateBuffer() {
        const validators = [
            this.validateBufferBreaks,
            this.validateBufferColors
        ];

        this.clearBufferValidation();

        return validators.reduce((isValid, validator) => {
            return isValid && validator.call(this);
        }, true);
    }

    validateBufferBreaks() {
        const hasNoDuplicates =
            this.buffer.length === [ ...new Set(this.buffer.map(c => c.break))].length;

        const hasNoEmptyValues =
            this.buffer.length === this.buffer.filter(c => c.break !== '').length;

        return hasNoDuplicates && hasNoEmptyValues;
    }

    validateBufferColors() {
        // Should probably use a regex here to ensure hex or rgb values
        return this.buffer.length === this.buffer.filter(c => c.color !== '').length;
    }

    isNextNumberAvailable(n) {
        return !this.buffer.find(c => c.break === n + 1);
    }

    shouldAllowDelete() {
        return this.buffer.length > 1;
    }

    colorToBackground(color) {
        return {
            'background': color
        };
    }

    toggleMaskColor(ind, colorBreak) {
        if (!this.maskedValues || !this.maskedValues.includes(colorBreak)) {
            this.maskedValues = [...(this.maskedValues || []), colorBreak];
        } else {
            this.maskedValues = _.filter(this.maskedValues, (x) => x !== colorBreak);
        }
        this.onBufferChanged();
    }

}
