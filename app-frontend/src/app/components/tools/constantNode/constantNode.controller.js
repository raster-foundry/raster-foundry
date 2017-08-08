export default class ConstantNodeController {
    constructor() {}

    $onInit() {
        let size = this.model.get('size');
        this.model.set('size', {width: size.width, height: 125});

        this.value = parseFloat(this.model.get('value'));
    }

    resetValue() {
        this.value = parseFloat(this.model.get('value'));
        this.onValueChange();
    }

    onValueChange() {
        this.onChange({override: {id: this.model.get('id'), value: this.value}});
    }
}
