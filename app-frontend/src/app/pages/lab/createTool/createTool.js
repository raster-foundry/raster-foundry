class LabCreateToolController {
    constructor(
        $log, $state,
        toolService
    ) {
        this.$log = $log;
        this.$state = $state;

        this.toolService = toolService;
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
        });
    }

    onToolParameterChange(nodeid, project, band, override, position) {
        if (project && typeof band === 'number' && band >= 0) {
            this.toolService.updateToolRunSource(this.tool, nodeid, project.id, band);
        }
        if (override) {
            this.toolService.updateToolRunConstant(this.tool, override.id, override);
        }
        if (position) {
            let metadata = this.toolService.getToolRunMetadata(this.tool, nodeid);
            this.toolService.updateToolRunMetadata(
                this.tool,
                nodeid,
                Object.assign({}, metadata, {
                    positionOverride: position
                })
            );
        }
    }

    createTool() {
        this.createInProgress = true;
        this.clearWarning();
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

    setWarning(text) {
        this.warning = text;
    }

    clearWarning() {
        delete this.warning;
    }
}

export default angular.module('pages.lab.createTool', [])
    .controller('LabCreateToolController', LabCreateToolController);
