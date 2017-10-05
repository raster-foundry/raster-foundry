export default class ConstantNodeController {
    constructor() {}

    $onInit() {
        let size = this.model.get('size');
        this.model.set('size', {width: size.width, height: 125});

        this.value = parseFloat(this.model.get('value'));
    }

    $onChanges(changes) {
        if (changes.node && changes.node.currentValue) {
            this.value = changes.node.currentValue.constant;
        }
    }

    resetValue() {
        this.value = parseFloat(this.model.get('value'));
        this.onValueChange();
    }

    onValueChange() {
        this.onChange({override: {id: this.model.get('id'), constant: this.value}});
    }
}
