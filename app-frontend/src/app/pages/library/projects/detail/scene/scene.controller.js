class ProjectSceneController {
    constructor( // eslint-disable-line max-params
        $log, $state, sceneService, projectService, $uibModal, mapService
    ) {
        'ngInject';
        this.$log = $log;
        this.$state = $state;
        this.projectService = projectService;
        this.sceneService = sceneService;
        this.$uibModal = $uibModal;
        this.getMap = () => mapService.getMap('scene');

        this.mapOptions = {
            static: true,
            fitToGeojson: true
        };

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
                        this.addThumbnailToMap();
                    },
                    () => {
                        this.$state.go('^.scenes');
                    }
                );
            } else {
                this.$state.go('^.scenes');
            }
        } else {
            this.addThumbnailToMap();
        }
    }

    addThumbnailToMap() {
        this.getMap().then((map) => {
            map.setThumbnail(this.scene, true);
        });
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
        this.projectService.removeScenesFromProject(this.projectId, [this.sceneId]).then(
            () => {
                this.$state.go('^.scenes');
            },
            (err) => {
                this.$log.error('Unable to remove scene from project:', err);
            });
    }
}

export default ProjectSceneController;
