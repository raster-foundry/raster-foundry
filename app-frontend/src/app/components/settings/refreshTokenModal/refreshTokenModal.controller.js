export default class refreshTokenModalController {
    constructor() {
        'ngInject';
        this.refreshToken = this.resolve.refreshToken;
        this.showToken = false;
    }
    toggleTokenVisibility() {
        this.showToken = !this.showToken;
    }
}
