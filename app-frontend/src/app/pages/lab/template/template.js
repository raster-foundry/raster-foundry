import _ from 'lodash';
import LabActions from '_redux/actions/lab-actions';

class LabTemplateController {
    constructor(
        $log, $state, $scope, $ngRedux, $window,
        analysisService, authService
    ) {
        this.$log = $log;
        this.$state = $state;

        this.analysisService = analysisService;
        this.authService = authService;
        this.$window = $window;

        let unsubscribe = $ngRedux.connect(this.mapStateToThis, LabActions)(this);
        $scope.$on('$destroy', unsubscribe);
    }

    $onInit() {
        this.template = this.$state.params.template;
        this.templateId = this.$state.params.templateid;

        this.authService.getCurrentUser().then(user => {
            this.user = user.id;
        });

        if (this.templateId && !this.template) {
            this.fetchTemplate();
        } else if (!this.templateId) {
            this.$state.go('lab.browse.templates');
        } else {
            this.templateDefinition = this.template.definition;
            this.analysis = this.analysisService.generateAnalysis(this.template);
            this.loadAnalysis(this.analysis, {readonly: true, controls: false});
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
            this.loadAnalysis(this.analysis, {readonly: true, controls: false});
        });
    }

    createAnalysis() {
        this.createInProgress = true;
        let analysisPromise = this.analysisService.createAnalysis(
            Object.assign(this.analysis, {name: this.template.title})
        );
        analysisPromise.then(analysis => {
            this.$state.go('lab.analysis', {analysisid: analysis.id});
        }, () => {
            this.$log('Error creating analysis');
        }).finally(() => {
            this.createInProgress = false;
        });
        return analysisPromise;
    }

    onDelete() {
        let answer = this.$window.confirm('Are you sure you want to delete this template?');
        if (answer) {
            this.analysisService.deleteTemplate(this.template.id).then(() => {
                this.$state.go('lab.browse.templates');
            });
        }
    }

    onEditClick() {
        this.oldTemplate = _.cloneDeep(this.template);
        this.editing = true;
    }

    onSaveClick() {
        this.analysisService.updateTemplate(this.template).then(() => {
            this.editing = false;
        }, (err) => {
            this.onCancelClick();
            throw new Error('There was an error updating a template: ', err);
        });
    }

    onCancelClick() {
        this.template = this.oldTemplate;
        this.editing = false;
    }
}

export default angular.module('pages.lab.template', [])
    .controller('LabTemplateController', LabTemplateController);
