export default class SelectedScenesModalController {
    constructor() {
        'ngInject';
        this.refreshToken = this.resolve.refreshToken;
        this.showToken = false;
    }
    toggleTokenVisibility() {
        this.showToken = !this.showToken;
    }
}
