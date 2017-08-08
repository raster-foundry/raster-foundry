export default class DiagramNodeHeaderController {
    constructor($document, $scope) {
        'ngInject';
        this.$document = $document;
        this.$scope = $scope;
    }

    $onChanges(changes) {
        if (changes.model && changes.model.currentValue) {
            this.cellType = this.model.get('cellType');
            this.cellTitle = this.model.get('title');
            this.menuItems = this.model.get('contextMenu');
        }
    }

    get typeMap() {
        return {
            'function': 'Function',
            'src': 'Input',
            'const': 'Constant'
        };
    }

    toggleMenu() {
        let initialClick = true;
        const onClick = () => {
            if (!initialClick) {
                this.showMenu = false;
                this.$document.off('click', this.clickListener);
                this.$scope.$evalAsync();
            } else {
                initialClick = false;
            }
        };
        if (!this.showMenu) {
            this.showMenu = true;
            this.clickListener = onClick;
            this.$document.on('click', onClick);
        } else {
            this.showMenu = false;
            this.$document.off('click', this.clickListener);
            delete this.clickListener;
        }
    }

    setHeight(height) {
        this.model.set('size', {width: 300, height: height});
    }
}
