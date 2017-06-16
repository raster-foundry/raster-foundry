export default class TokenItem {
    constructor(projectService, $uibModal) {
        'ngInject';
        this.projectService = projectService;
        this.$uibModal = $uibModal;
    }

    $onInit() {
        this.editing = false;
        this.newName = this.token.name;
        this.projectService.query({id: this.token.project}).then(
            (project) => {
                this.project = project;
            }
        );
    }

    deleteToken() {
        this.onDelete({data: this.token});
    }

    startEditing() {
        this.editing = true;
    }

    onEditComplete(name) {
        this.editing = false;
        this.onUpdate({token: this.token, name: name});
    }

    onEditCancel() {
        this.newName = this.token.name;
        this.editing = false;
    }

    publishModal() {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        this.activeModal = this.$uibModal.open({
            component: 'rfProjectPublishModal',
            resolve: {
                project: () => this.project,
                tileUrl: () => this.projectService.getProjectLayerURL(this.project),
                shareUrl: () => this.projectService.getProjectShareURL(this.project)
            }
        });

        return this.activeModal;
    }
}
