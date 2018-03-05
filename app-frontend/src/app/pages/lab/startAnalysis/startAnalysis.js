import LabActions from '_redux/actions/lab-actions';

class LabStartAnalysisController {
    constructor(
        $log, $state, $scope, $ngRedux,
        analysisService
    ) {
        this.$log = $log;
        this.$state = $state;

        this.analysisService = analysisService;

        let unsubscribe = $ngRedux.connect(this.mapStateToThis, LabActions)(this);
        $scope.$on('$destroy', unsubscribe);
    }

    $onInit() {
        this.template = this.$state.params.template;
        this.templateId = this.$state.params.templateid;
        this.analysisName = '';

        if (this.templateId && !this.template) {
            this.fetchTemplate();
        } else if (!this.templateId) {
            this.fromScratch = true;
        } else {
            this.templateDefinition = this.template.definition;
            this.analysis = this.analysisService.generateAnalysis(this.template);
            this.loadAnalysis(this.analysis, {readonly: true, controls: true});
        }
    }

    fetchTemplate() {
        this.loadingTemplate = true;
        this.templateRequest = this.analysisService.getTemplate(this.templateId);
        this.templateRequest.then(template => {
            this.template = template;
            this.templateDefinition = template.definition;
            this.loadingTemplate = false;
            this.analysis = this.analysisService.generateAnalysis(this.template);
            this.loadAnalysis(this.analysis, {readonly: true, controls: true});
        });
    }

    createAnalysis() {
        this.createInProgress = true;
        if (this.analysisName.length) {
            this.analysis.name = this.analysisName;
        } else {
            this.analysis.name = this.template.title;
        }
        let analysisPromise = this.analysisService.createAnalysis(this.analysis);
        analysisPromise.then(analysis => {
            this.$state.go('lab.analysis', {analysisid: analysis.id});
        }, () => {
            this.setWarning(
                `There was an error creating a analysis with the specified inputs.
                 Please verify that all inputs are defined.`
            );
        }).finally(() => {
            this.createInProgress = false;
        });
        return analysisPromise;
    }
}

export default angular.module('pages.lab.startAnalysis', [])
    .controller('LabStartAnalysisController', LabStartAnalysisController);
