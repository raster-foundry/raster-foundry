export default class ValidateValueString {
    constructor() {
        this.restrict = 'A';
        this.require = 'ngModel';
        this.scope = {};
    }

    controller($scope, reclassifyService) {
        'ngInject';
        $scope.reclassifyService = reclassifyService;
    }

    link($scope, $element, $attr, $ctrl) {
        $ctrl.$validators.rangeString = (modelValue, viewValue) => {
            let value = modelValue || viewValue;
            return $scope.reclassifyService.isValueStrValid(value);
        };
    }
}
