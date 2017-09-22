export default class ClassifyNodeController {
    constructor($uibModal, reclassifyService, toolService) {
        'ngInject';
        this.reclassifyService = reclassifyService;
        this.$uibModal = $uibModal;
        this.toolService = toolService;
    }

    $onInit() {
        let size = this.model.get('size');
        this.model.set('size', {width: size.width, height: 275});
        this.value = parseFloat(this.model.get('value'));
    }

    $onChanges() {
        this.breaks = this.model.attributes.classMap.classifications;
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
        const breakList = Object.keys(this.breaks).map(b => +b);
        return this.reclassifyService.breaksToRangeObjects(breakList);
    }

    getClassCount() {
        return Object.keys(this.breaks).length;
    }

    showReclassifyModal() {
        this.activeModal = this.$uibModal.open({
            component: 'rfReclassifyModal',
            resolve: {
                classifications: () => this.model.get('classMap').classifications,
                model: () => this.model,
                child: () => this.child
            }
        });

        this.activeModal.result.then(breaks => {
            this.breaks = breaks;
            this.model.set('classMap', {classifications: breaks});
            this.ranges = this.getRanges();
            this.onChange({
                override: {
                    id: this.model.get('id'),
                    classMap: {
                        classifications: breaks
                    }
                }
            });
        });
    }
}
