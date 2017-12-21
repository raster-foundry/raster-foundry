import angular from 'angular';
import sceneDownloadModalTpl from './sceneDownloadModal.html';

const SceneDownloadModalComponent = {
    templateUrl: sceneDownloadModalTpl,
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    },
    controller: 'SceneDownloadModalController'
};

class SceneDownloadModalController {
    constructor(sceneService) {
        'ngInject';
        this.sceneService = sceneService;
    }

    $onInit() {
        this.downloads = [];
        this.isLoading = false;
        if (this.resolve.scene) {
            const scene = this.resolve.scene;
            this.isLoading = true;
            this.sceneService.getDownloadableImages(scene).then(images => {
                const imageSet = images.map(image => {
                    return {
                        filename: image.filename,
                        uri: image.downloadUri,
                        metadata: image.metadataFiles || []
                    };
                });
                this.downloads = [{
                    label: scene.name,
                    metadata: scene.metadataFiles || [],
                    images: imageSet
                }];

                this.isLoading = false;
            });
        }
    }
}

const SceneDownloadModalModule = angular.module('components.scenes.sceneDownloadModal', []);

SceneDownloadModalModule.component('rfSceneDownloadModal', SceneDownloadModalComponent);
SceneDownloadModalModule.controller('SceneDownloadModalController', SceneDownloadModalController);

export default SceneDownloadModalModule;
