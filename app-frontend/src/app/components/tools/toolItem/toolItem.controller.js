/* global BUILDCONFIG */

export default class ToolItemController {
    constructor() {
        'ngInject';
    }

    $onInit() {
        this.BUILDCONFIG = BUILDCONFIG;
    }
}
