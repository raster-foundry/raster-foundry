import LabActions from '../../../redux/actions/lab-actions';

class LabCreateToolController {
    constructor(
        $log, $state, $scope, $ngRedux,
        toolService
    ) {
        this.$log = $log;
        this.$state = $state;

        this.toolService = toolService;

        let unsubscribe = $ngRedux.connect(this.mapStateToThis, LabActions)(this);
        $scope.$on('$destroy', unsubscribe);
    }

    $onInit() {
        this.template = this.$state.params.template;
        this.templateId = this.$state.params.templateid;
        this.toolName = '';

        if (this.templateId && !this.template) {
            this.fetchTemplate();
        } else if (!this.templateId) {
            this.fromScratch = true;
        } else {
            this.templateDefinition = this.template.definition;
            this.tool = this.toolService.generateToolRun(this.template);
            this.loadTool(this.tool, true);
        }
    }

    fetchTemplate() {
        this.loadingTemplate = true;
        this.templateRequest = this.toolService.get(this.templateId);
        this.templateRequest.then(template => {
            this.template = template;
            this.templateDefinition = template.definition;
            this.loadingTemplate = false;
            this.tool = this.toolService.generateToolRun(this.template);
            this.loadTool(this.tool, true);
        });
    }

    createTool() {
        this.createInProgress = true;
        if (this.toolName.length) {
            this.tool.name = this.toolName;
        } else {
            this.tool.name = this.template.title;
        }
        let toolRunPromise = this.toolService.createToolRun(this.tool);
        toolRunPromise.then(tr => {
            this.$state.go('lab.tool', {toolid: tr.id});
        }, () => {
            this.setWarning(
                `There was an error creating a tool with the specified inputs.
                 Please verify that all inputs are defined.`
            );
        }).finally(() => {
            this.createInProgress = false;
        });
        return toolRunPromise;
    }
}

export default angular.module('pages.lab.createTool', [])
    .controller('LabCreateToolController', LabCreateToolController);
