import labRunTpl from './run.html';

const labRun = {
    templateUrl: labRunTpl,
    controller: 'labRunController',
    bindings: {
        model: '<?'
    }
};

export default labRun;
