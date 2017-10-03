export default class ClassifyNodeController {
    constructor($uibModal, reclassifyService, toolService) {
        'ngInject';
        this.$uibModal = $uibModal;
        this.reclassifyService = reclassifyService;
        this.toolService = toolService;
    }

    $onInit() {
        // Initialize breaks from tool definition
        if (!this.breaks) {
            const classmap = this.model.attributes.classMap.classifications;
            this.breaks = this.classmapToBreaks(classmap);
        }
    }

    $onChanges(changes) {
        if (changes.model && changes.model.currentValue) {
            this.id = changes.model.currentValue.get('id');

            // Sets the visible height of the node
            this.model.set('size', Object.assign(
                {},
                changes.model.currentValue.get('size'),
                { height: 275 }
            ));
        }

        // When a tool-run node is available, reset breaks
        if (changes.node && changes.node.currentValue) {
            const classmap = changes.node.currentValue.classMap.classifications;
            this.breaks = this.classmapToBreaks(classmap);
        }
    }

    /**
     * Takes a class-map and returns an array of breaks
     *
     * @param {object} classmap classmap object
     * @returns {array} array of break objects
     * @memberof ClassifyNodeController
     *
     * @example
     * // returns [{ break: 128, value: 0, start: "Min"}, { break: 255, value: 1, start: 128}]
     * preprocessBreaks({
     *     "128.0": 0,
     *     "255.0": 1
     * });
    * */
    classmapToBreaks(classmap) {
        return Object.keys(classmap).map((b, i, keys) => {
            const prev = keys[i - 1];
            // eslint-disable-next-line eqeqeq
            const start = prev != null ? +prev : 'Min';
            return {
                break: +b,
                value: classmap[b],
                start
            };
        });
    }

    /**
     * Converts array of break objects to a classmap
     *
     * @param {array} breaks array of break objects
     * @returns {object} classmap object
     * @memberof ClassifyNodeController
     */
    breaksToClassmap(breaks) {
        return breaks.reduce((acc, b) => {
            acc[b.break] = b.value;
            return acc;
        }, {});
    }

    showReclassifyModal() {
        this.activeModal = this.$uibModal.open({
            component: 'rfReclassifyModal',
            resolve: {
                breaks: () => this.breaks.map(b => Object.assign({}, b)),
                model: () => this.model,
                child: () => this.child
            }
        });

        this.activeModal.result.then(breaks => {
            const classmap = this.breaksToClassmap(breaks);
            this.breaks = breaks;
            this.model.set('classMap', {classifications: classmap});
            this.onChange({
                override: {
                    id: this.id,
                    classMap: {
                        classifications: classmap
                    }
                }
            });
        });
    }
}
