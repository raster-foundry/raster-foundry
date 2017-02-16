import labEditTpl from './edit.html';

const labEdit = {
    templateUrl: labEditTpl,
    controller: 'labEditController',
    bindings: {
        model: '<?'
    }
};

export default labEdit;
