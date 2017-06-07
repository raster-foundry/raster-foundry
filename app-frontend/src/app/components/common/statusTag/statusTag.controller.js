export default class StatusTagController {
    constructor(statusService) {
        'ngInject';
        this.statusService = statusService;
    }

    $onInit() {
        this.statusFields = this.statusService.getStatusFields(this.entityType, this.status);
    }

    getStatusString() {
        return this.statusFields.label;
    }

    getStatusClass() {
        return `${this.statusFields.color}-tag`;
    }
}
