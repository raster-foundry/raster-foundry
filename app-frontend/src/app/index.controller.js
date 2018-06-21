export default class IndexController {
    constructor($scope) {
        'ngInject';
        $scope.$on('$stateChangeStart', () => {
            $scope.showLoadingIndicator = true;
        });
        $scope.$on('$stateChangeSuccess', () => {
            $scope.showLoadingIndicator = false;
        });
    }
}
