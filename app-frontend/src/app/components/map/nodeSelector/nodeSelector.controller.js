export default class NodeSelectorController {
    constructor(
        $log, $element, $scope, $timeout, $document
    ) {
        'ngInject';
        this.$log = $log;
        this.$element = $element;
        this.$scope = $scope;
        this.$timeout = $timeout;
        this.$document = $document;
    }

    $onInit() {
        this.$timeout(() => this.setPosition(this.position), 100);
    }

    $onChanges(changes) {
        if (changes.position && changes.position.currentValue) {
            this.setPosition(changes.position.currentValue);
        }
        if (changes.selected && changes.selected.currentValue) {
            if (this.position) {
                this.$scope.$evalAsync(() => {
                    this.setPosition(this.position);
                });
            }
        }
        if (changes.nodeMap && changes.nodeMap.currentValue) {
            this.nodes = Array.from(changes.nodeMap.currentValue).map((entry) => {
                return entry[1];
            });
        }
    }

    setPosition(position) {
        let width = this.$element[0].offsetWidth;
        let sideMult = position.side === 'left' ? -1 : 1;
        let x = position.offset * sideMult;
        if (position.side === 'left') {
            x = x - width;
        }
        this.$element.css({
            left: `${x + position.x}px`
        });
    }

    startSelecting() {
        let initialClick = true;
        const onClick = () => {
            if (!initialClick) {
                this.showSearch = false;
                this.$document.off('click', this.clickListener);
                this.setPosition(this.position);
                this.$scope.$evalAsync();
            } else {
                initialClick = false;
            }
        };

        if (!this.showSearch) {
            this.showSearch = true;
            this.clickListener = onClick;
            this.$document.on('click', onClick);
            this.$timeout(() => {
                this.setPosition(this.position);
                this.$element.find('.node-filter').focus();
            }, 100);
        } else {
            this.showSearch = false;
            this.setPosition(this.position);
            this.$document.off('click', this.clickListener);
            delete this.clickListener;
        }
    }

    get selectedLabel() {
        return this.nodeMap.get(this.selected).label;
    }

    get selectedType() {
        return this.nodeMap.get(this.selected).type;
    }

    selectNode(node) {
        this.selected = node.id;
        this.onSelect({node: node.id, side: this.side});
        this.showSearch = false;
        this.selectFilter = '';
        this.$timeout(() => this.setPosition(this.position), 100);
    }
}
