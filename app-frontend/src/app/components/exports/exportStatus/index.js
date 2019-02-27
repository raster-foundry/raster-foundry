import tpl from './index.html';

const component = {
    bindings: {
        export: '<'
    },
    templateUrl: tpl
};

export default angular
    .module('components.exports.exportStatus', [])
    .component('rfExportStatus', component)
    .name;
