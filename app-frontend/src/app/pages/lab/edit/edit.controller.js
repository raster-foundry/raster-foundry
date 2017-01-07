export default class LabEditController {
    constructor($scope, $element) {
        'ngInject';
        this.$scope = $scope;
        this.$element = $element;
        this.$parent = $scope.$parent.$ctrl;
        this.isShowingParams = false;
    }

    $onInit() {
        if (this.$parent.toolRequest) {
            this.$parent.toolRequest.then(t => {
                this.cellLabel = t.title;
            });
        }
    }

    showParams() {
        this.isShowingParams = true;
        this.$scope.$evalAsync();
    }

    hideParams() {
        this.isShowingParams = false;
        this.$scope.$evalAsync();
    }
}
