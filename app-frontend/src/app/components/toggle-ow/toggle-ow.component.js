import toggleTpl from './toggle-ow.html';
const rfToggle = {
    templateUrl: toggleTpl,
    controller: 'ToggleOWController',
    transclude: true,
    bindings: {
        value: '<',
        onChange: '&',
        className: '@'
    },
    controllerAs: '$ctrl'
};

export default rfToggle;
