export default class ToggleOldController {
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
    toggle($event) {
        $event.stopPropagation();
        $event.preventDefault();
        this.onChange({value: !this.model});
    }
}
