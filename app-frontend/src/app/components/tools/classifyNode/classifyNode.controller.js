export default class ClassifyNodeController {
    constructor($uibModal, reclassifyService) {
        'ngInject';
        this.reclassifyService = reclassifyService;
        this.$uibModal = $uibModal;
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
        return this.reclassifyService.breaksToRangeObjects(
            Object.keys(this.breaks).map((brk) => Number(brk))
        );
    }

    showReclassifyModal() {
        this.activeModal = this.$uibModal.open({
            component: 'rfReclassifyModal',
            resolve: {
                classifications: () => this.breaks
            }
        });

        this.activeModal.result.then(breaks => {
            this.breaks = breaks;
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
