export default class ToggleController {
    constructor() {
        'ngInject';
    }
    $onInit() {
        if (!this.model) {
            this.model = false;
        }
    }
    $onChanges(changes) {
        if ('model' in changes && changes.dataModel.currentValue) {
            this.model = changes.dataModel.currentValue;
        }
    }
    toggle() {
        this.model = !this.model;
        this.onChange({value: this.model});
    }
}
