export default class ImportModalController {
    constructor($state, projectService) {
        'ngInject';
        this.$state = $state;
        this.projectService = projectService;
    }

    $onInit() {
        this.steps = [
            'IMPORT',
            'IMPORT_SUCCESS'
        ];
        this.importType = 'local';
        this.currentStep = this.steps[0];
        this.allowNext = true;
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
            this.$state.go('browse', {projectid: this.project.id});
        }
    }

    createProject() {
        return this.projectService.createProject(this.projectBuffer.name);
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
                        this.gotoNextStep();
                    }).finally(() => {
                        this.allowNext = true;
                        this.isCreatingProject = false;
                    });
                }
            } else if (
                this.currentStepIs('ADD_SCENES') &&
                this.projectAttributeIs('addType', 'public')
            ) {
                this.gotoSceneBrowser();
            } else {
                this.gotoNextStep();
            }
        }
    }

    closeWithData(data) {
        this.close({$value: data});
    }
}
