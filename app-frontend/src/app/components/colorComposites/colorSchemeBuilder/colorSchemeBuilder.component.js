import colorSchemeBuilderTpl from './colorSchemeBuilder.html';
const rfColorSchemeBuilder = {
    templateUrl: colorSchemeBuilderTpl,
    controller: 'ColorSchemeBuilderController',
    bindings: {
        colorScheme: '<',
        maskedValues: '<',
        onChange: '&'
    },
    controllerAs: '$ctrl'
};

export default rfColorSchemeBuilder;
