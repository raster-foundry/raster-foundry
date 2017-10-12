export default class LabNavbarController {
    constructor(toolService, $state, $uibModal) {
        'ngInject';

        this.toolService = toolService;
        this.$state = $state;
        this.$uibModal = $uibModal;

        this.toolId = this.$state.params.toolid;
        this.toolService.loadTool(this.toolId)
            .then((tool) => {
                this.tool = tool;
            });
    }
}
