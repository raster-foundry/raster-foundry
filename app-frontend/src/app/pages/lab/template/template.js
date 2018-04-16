import _ from 'lodash';
import WorkspaceActions from '_redux/actions/workspace-actions';

class LabTemplateController {
    constructor(
        $log, $state, $scope, $ngRedux, $window,
        templateService, authService, modalService, workspaceService
    ) {
        this.$log = $log;
        this.$state = $state;

        this.templateService = templateService;
        this.authService = authService;
        this.modalService = modalService;
        this.workspaceService = workspaceService;
        this.$window = $window;

        let unsubscribe = $ngRedux.connect(this.mapStateToThis, WorkspaceActions)(this);
        $scope.$on('$destroy', unsubscribe);
    }

    $onInit() {
        this.template = this.$state.params.template;
        this.templateId = this.$state.params.templateid;

        this.authService.getCurrentUser().then(user => {
            this.user = user.id;
        });

        this.setDisplayOptions({readonly: true, controls: false});
        if (this.templateId && !this.template) {
            this.fetchTemplate();
        } else if (!this.templateId) {
            this.$state.go('lab.browse.templates');
        } else {
            this.setWorkspace(
                this.workspaceService.workspaceFromTemplate(this.template)
            );
        }
    }

    fetchTemplate() {
        this.loadingTemplate = true;
        this.templateRequest = this.templateService.getTemplate(this.templateId);
        this.templateRequest.then(template => {
            this.template = template;
            this.loadingTemplate = false;

            this.setWorkspace(
                this.workspaceService.workspaceFromTemplate(this.template)
            );
        });
    }

    createWorkspace() {
        this.modalService.open({
            component: 'rfWorkspaceCreateModal',
            resolve: {
                template: () => this.template
            }
        });
    }

    onDelete() {
        let answer = this.$window.confirm('Are you sure you want to delete this template?');
        if (answer) {
            this.templateService.deleteTemplate(this.template.id).then(() => {
                this.$state.go('lab.browse.templates');
            });
        }
    }

    onEditClick() {
        this.oldTemplate = _.cloneDeep(this.template);
        this.editing = true;
    }

    onSaveClick() {
        this.templateService.updateTemplate(this.template).then(() => {
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
