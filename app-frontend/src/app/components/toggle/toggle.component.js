import toggleTpl from './toggle.html';
const rfToggle = {
    templateUrl: toggleTpl,
    controller: 'ToggleController',
    bindings: {
        model: '=',
        onChange: '&'
    },
    controllerAs: '$ctrl'
};

export default rfToggle;
