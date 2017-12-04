export default class TokenItem {
    constructor(projectService, modalService) {
        'ngInject';
        this.projectService = projectService;
        this.modalService = modalService;
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
        this.modalService.open({
            component: 'rfProjectPublishModal',
            resolve: {
                project: () => this.project,
                tileUrl: () => this.projectService.getProjectLayerURL(this.project),
                shareUrl: () => this.projectService.getProjectShareURL(this.project)
            }
        });
    }
}
