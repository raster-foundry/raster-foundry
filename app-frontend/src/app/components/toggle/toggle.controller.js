export default class ToggleController {
    constructor() {
        'ngInject';
    }
    $onInit() {
        this.currentValue = this.value || this.model || false;
    }
    $onChanges(changes) {
        if ('value' in changes) {
            this.currentValue = changes.value.currentValue;
        }
    }
    toggle($event) {
        $event.stopPropagation();
        $event.preventDefault();
        this.currentValue = !this.currentValue;

        // Mutate `this.model` only if it is defined (which would
        // indicate we are using two-way binding)
        // eslint-disable-next-line no-eq-null, eqeqeq
        if (this.model != null) {
            this.model = this.currentValue;
        }

        this.onChange({value: this.currentValue});
    }
}
