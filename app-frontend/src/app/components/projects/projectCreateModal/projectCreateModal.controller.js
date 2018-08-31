/* global BUILDCONFIG */

export default class ProjectCreateModalController {
    constructor($state, projectService, modalService) {
        'ngInject';
        this.$state = $state;
        this.projectService = projectService;
        this.modalService = modalService;
    }

    $onInit() {
        this.BUILDCONFIG = BUILDCONFIG;
        this.steps = [
            'TYPE',
            'ADD_SCENES'
        ];
        this.currentStep = this.steps[0];
        this.projectBuffer = {
            isAOIProject: false,
            addType: 'public'
        };
        this.allowNext = true;
        this.returnData = Object.assign({}, this.resolve.returnData);
    }

    projectAttributeIs(attr, value) {
        if (this.projectBuffer.hasOwnProperty(attr)) {
            return this.projectBuffer[attr] === value;
        }
        return false;
    }

    setProjectAttribute(attr, value) {
        this.projectBuffer[attr] = value;
    }

    currentStepIs(step) {
        if (step.constructor === Array) {
            return step.reduce((acc, cs) => {
                return acc || this.currentStepIs(cs);
            }, false);
        }
        return this.currentStep === step;
    }

    currentStepIsNot(step) {
        if (step.constructor === Array) {
            return step.reduce((acc, cs) => {
                return acc && this.currentStepIsNot(cs);
            }, true);
        }
        return this.currentStep !== step;
    }

    getCurrentStepIndex() {
        return this.steps.indexOf(this.currentStep);
    }

    hasNextStep() {
        const stepLimit = this.steps.length - 1;
        return this.getCurrentStepIndex() < stepLimit;
    }

    hasPreviousStep() {
        return this.getCurrentStepIndex() > 0;
    }

    gotoPreviousStep() {
        if (this.hasPreviousStep()) {
            this.currentStep = this.steps[this.getCurrentStepIndex() - 1];
        }
    }

    gotoNextStep() {
        if (this.hasNextStep()) {
            this.currentStep = this.steps[this.getCurrentStepIndex() + 1];
        }
    }

    gotoStep(step) {
        const stepIndex = this.steps.indexOf(step);
        if (stepIndex) {
            this.currentStep = this.step;
        }
    }

    gotoSceneBrowser() {
        if (this.project) {
            this.close();
            this.$state.go('projects.edit.browse', {projectid: this.project.id});
        }
    }

    gotoAOIParameters() {
        if (this.project) {
            this.close();
            this.$state.go('projects.edit.aoi-parameters', {projectid: this.project.id});
        }
    }

    startImport() {
        this.closeWithData();

        this.modalService.open({
            component: 'rfSceneImportModal',
            backdrop: 'static',
            keyboard: false,
            resolve: {
                project: () => this.project,
                origin: () => 'projectCreate'
            }
        });
    }

    createProject() {
        return this.projectService.createProject(this.projectBuffer.name, this.projectBuffer);
    }

    validateProjectName() {
        if (this.projectBuffer.name) {
            this.showProjectCreateError = false;
            return true;
        }
        this.projectCreateErrorText = 'A name is needed for your new project';
        this.showProjectCreateError = true;
        return false;
    }

    handleNext() {
        if (this.allowNext) {
            if (this.currentStepIs('TYPE')) {
                if (this.validateProjectName()) {
                    this.allowNext = false;
                    this.isCreatingProject = true;
                    this.createProject().then(p => {
                        this.project = p;
                        this.returnData.reloadProjectList = true;
                        this.gotoNextStep();
                    }).finally(() => {
                        this.allowNext = true;
                        this.isCreatingProject = false;
                    });
                }
            } else if (this.currentStepIs('ADD_SCENES')) {
                let isPublic = this.projectAttributeIs('addType', 'public');
                if (this.projectBuffer.isAOIProject && isPublic) {
                    this.gotoAOIParameters();
                } else if (isPublic) {
                    this.gotoSceneBrowser();
                } else {
                    this.startImport();
                }
            } else {
                this.gotoNextStep();
            }
        }
    }

    closeWithData() {
        this.close({$value: this.returnData});
    }
}
