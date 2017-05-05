class labController {
    constructor($log, $state, $uibModal, toolService) {
        'ngInject';
        this.$log = $log;
        this.$state = $state;
        this.$uibModal = $uibModal;
        this.toolService = toolService;
    }

    $onInit() {
        this.toolData = this.$state.params.toolData;
        this.toolId = this.$state.params.toolid;
        if (!this.tool) {
            if (this.toolId) {
                this.fetchTool();
            } else {
                this.selectToolModal();
            }
        }
    }

    fetchTool() {
        this.loadingTool = true;
        this.toolRequest = this.toolService.get(this.toolId);
        this.toolRequest.then(tool => {
            this.tool = tool;
            this.loadingTool = false;
        });
    }

    selectToolModal() {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        this.activeModal = this.$uibModal.open({
            component: 'rfSelectToolModal',
            backdrop: 'static',
            keyboard: false,
            resolve: {
                requireSelection: () => true
            }
        }).result.then((t) => {
            this.$state.go(this.$state.current, {toolid: t.id});
        });

        return this.activeModal;
    }
}

export default labController;
