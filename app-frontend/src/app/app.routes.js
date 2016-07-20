export default function routing($urlRouterProvider, $locationProvider, $stateProvider) {
    'ngInject';
    $locationProvider.html5Mode(true);
    $urlRouterProvider.otherwise('/login');
    $stateProvider
        .state('login', {
            url: '/login',
            template: '<rf-login></rf-login>'
        })
        .state('library', {
            url: '/library',
            template: '<rf-library></rf-library>'
        });
}
