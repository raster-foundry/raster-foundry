// TODO finish this
export default class OperationNodeController {
    constructor($scope) {
        'ngInject';
        this.$scope = $scope;
    }

    $onChanges(changes) {
        if (changes.model && changes.model.currentValue) {
            this.model = changes.model.currentValue;
            this.cellType = this.model.get('cellType');
            this.cellTitle = this.model.get('title');
            this.cellOperation = this.model.get('operation');
            this.menuItems = this.model.get('contextMenu');

            // eslint-disable-next-line
            let inputLinks = Object.keys(this.model.graph._in[this.model.id]);
            this.inputModels = this.model.graph.attributes.cells.models.filter((model) => {
                return inputLinks.indexOf(model.id) > -1;
            }).map((link) => {
                return link.get('source').id;
            }).map((inputId) => {
                return this.model.graph.attributes.cells.models.find((model) => {
                    return model.id === inputId;
                });
            });
        }
    }
}
