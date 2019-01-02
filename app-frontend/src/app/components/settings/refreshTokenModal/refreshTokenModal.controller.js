export default class refreshTokenModalController {
    constructor() {
        'ngInject';
        this.showToken = false;
    }

    $onInit() {
        this.refreshToken = this.resolve.refreshToken;
    }

    toggleTokenVisibility() {
        this.showToken = !this.showToken;
    }
}
