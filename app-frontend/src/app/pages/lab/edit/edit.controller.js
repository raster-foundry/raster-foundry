export default class LabEditController {
    constructor($scope, $element) {
        'ngInject';
        this.$scope = $scope;
        this.$element = $element;
        this.isShowingParams = false;
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
