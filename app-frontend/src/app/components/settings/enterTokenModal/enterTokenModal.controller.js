export default class EnterTokenModalController {
    constructor() {
        'ngInject';
    }

    $onInit() {
        this.tokenType = this.resolve.tokenType;
        this.inputValue = this.resolve.token ? this.resolve.token : '';
    }
}
