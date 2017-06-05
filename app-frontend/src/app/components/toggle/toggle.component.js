import toggleTpl from './toggle.html';
const rfToggle = {
    templateUrl: toggleTpl,
    controller: 'ToggleController',
    transclude: true,
    bindings: {
        value: '<',
        onChange: '&',
        className: '@'
    },
    controllerAs: '$ctrl'
};

export default rfToggle;
