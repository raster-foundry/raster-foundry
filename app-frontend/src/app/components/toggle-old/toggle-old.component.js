import toggleTpl from './toggle-old.html';
const rfToggle = {
    templateUrl: toggleTpl,
    controller: 'ToggleOldController',
    transclude: true,
    bindings: {
        model: '=',
        onChange: '&',
        className: '@'
    },
    controllerAs: '$ctrl'
};

export default rfToggle;
