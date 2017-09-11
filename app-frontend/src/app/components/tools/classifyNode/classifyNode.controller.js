export default class ClassifyNodeController {
    constructor(reclassifyService) {
        'ngInject';
        this.reclassifyService = reclassifyService;
    }

    $onInit() {
        let size = this.model.get('size');
        this.model.set('size', {width: size.width, height: 275});
        this.value = parseFloat(this.model.get('value'));
    }

    $onChanges() {
        this.breaks = Object.keys(this.model.attributes.classMap.classifications);
        this.ranges = this.getRanges();
    }

    resetValue() {
        this.value = parseFloat(this.model.get('value'));
        this.onValueChange();
    }

    onValueChange() {
        this.onChange({override: {id: this.model.get('id'), value: this.value}});
    }

    getRanges() {
        const breaks = Object.keys(this.model.attributes.classMap.classifications);
        const ranges = this.reclassifyService.breaksToRangeObjects(breaks);
        return ranges;
    }
}
