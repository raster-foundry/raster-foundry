function routeConfig($urlRouterProvider, $stateProvider, resolverProvider) {
    'ngInject';

    $stateProvider
        .state('login', {
            url: '/login',
            templateUrl: require(
                '!!file-loader?name=templates/[name].[ext]!./pages/login/login.html'),
            controller: 'loginController',
            controllerAs: '$ctrl',
            resolve: {
                asyncPreloading: resolverProvider.loginPagePrealoading
            }
        });
    $urlRouterProvider.otherwise('/');
}

export default angular
    .module('index.routes', [])
    .config(routeConfig);

