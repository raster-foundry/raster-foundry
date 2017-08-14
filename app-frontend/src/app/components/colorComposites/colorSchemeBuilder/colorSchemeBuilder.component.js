import colorSchemeBuilderTpl from './colorSchemeBuilder.html';
const rfColorSchemeBuilder = {
    templateUrl: colorSchemeBuilderTpl,
    controller: 'ColorSchemeBuilderController',
    bindings: {
        colorScheme: '<',
        onChange: '&'
    },
    controllerAs: '$ctrl'
};

export default rfColorSchemeBuilder;
