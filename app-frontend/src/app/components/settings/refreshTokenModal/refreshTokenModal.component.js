// Component code
import refreshTokenModalTpl from './refreshTokenModal.html';

const RefreshTokenModalComponent = {
    templateUrl: refreshTokenModalTpl,
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    },
    controller: 'RefreshTokenModalController'
};

export default RefreshTokenModalComponent;
