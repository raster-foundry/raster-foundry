import colorSchemeDropdownTpl from './colorSchemeDropdown.html';
const rfColorSchemeDropdown = {
    templateUrl: colorSchemeDropdownTpl,
    controller: 'ColorSchemeDropdownController',
    bindings: {
        colorSchemeOptions: '<',
        onChange: '&'
    },
    controllerAs: '$ctrl'
};

export default rfColorSchemeDropdown;
