import colorSchemeDropdownTpl from './colorSchemeDropdown.html';
const rfColorSchemeDropdown = {
    templateUrl: colorSchemeDropdownTpl,
    controller: 'ColorSchemeDropdownController',
    bindings: {
        serializedSingleBandOptions: '<',
        onChange: '&'
    },
    controllerAs: '$ctrl'
};

export default rfColorSchemeDropdown;
