import toggleTpl from './toggle.html';
const rfToggle = {
    templateUrl: toggleTpl,
    controller: 'ToggleController',
    transclude: true,
    bindings: {
        value: '<',
        onChange: '&',
        radio: '<',
        className: '@'
    },
    controllerAs: '$ctrl'
};

export default rfToggle;
