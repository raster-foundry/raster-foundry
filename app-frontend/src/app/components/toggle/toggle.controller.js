export default class ToggleController {
    constructor() {
        'ngInject';
    }
    $onInit() {
        if (!this.model) {
            this.model = {
                value: false
            };
        }
    }
    $onChanges(changes) {
        if ('model' in changes && changes.dataModel.currentValue) {
            this.model = changes.dataModel.currentValue;
        }
    }
}
