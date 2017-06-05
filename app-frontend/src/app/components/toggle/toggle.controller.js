export default class ToggleController {
    constructor() {
        'ngInject';
    }
    $onInit() {
        this.currentValue = this.value || false;
    }
    $onChanges(changes) {
        if ('value' in changes) {
            this.currentValue = changes.value.currentValue;
        }
    }
    toggle($event) {
        $event.stopPropagation();
        $event.preventDefault();
        this.onChange({value: !this.currentValue});
    }
}
