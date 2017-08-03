import enterTokenModalTpl from './enterTokenModal.html';

const EnterTokenModalComponent = {
    templateUrl: enterTokenModalTpl,
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    },
    controller: 'EnterTokenModalController'
};

export default EnterTokenModalComponent;
