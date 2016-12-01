class ProjectSceneController {
    constructor($log, $state, sceneService, projectService, $uibModal) {
        'ngInject';
        this.$log = $log;
        this.$state = $state;
        this.projectService = projectService;
        this.sceneService = sceneService;
        this.$uibModal = $uibModal;

        this.scene = this.$state.params.scene;
        this.projectId = this.$state.params.projectid;
        this.sceneId = this.$state.params.sceneid;

        if (!this.scene) {
            if (this.sceneId) {
                this.loading = true;
                sceneService.query({id: this.sceneId}).then(
                    (scene) => {
                        this.scene = scene;
                        this.loading = false;
                    },
                    () => {
                        this.$state.go('^.scenes');
                    }
                );
            } else {
                this.$state.go('^.scenes');
            }
        }
    }

    downloadModal() {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        let images = this.scene.images.map((image) => {
            return {
                filename: image.filename,
                uri: image.sourceUri,
                metadata: image.metadataFiles || []
            };
        });

        let downloadSets = [{
            label: this.scene.name,
            metadata: this.scene.metadataFiles || [],
            images: images
        }];

        this.activeModal = this.$uibModal.open({
            component: 'rfDownloadModal',
            resolve: {
                downloads: () => downloadSets
            }
        });
    }

    removeScene() {
        this.projectService.removeSceneFromProject({
            projectId: this.projectId,
            sceneId: this.sceneId
        }).then(
            () => {
                this.$state.go('^.scenes');
            },
            (err) => {
                this.$log.error('Unable to remove scene from project:', err);
            });
    }
}

export default ProjectSceneController;
