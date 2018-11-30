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
    constructor(sceneService, $timeout) {
        'ngInject';
        this.sceneService = sceneService;
        this.$timeout = $timeout;
    }

    $onInit() {
        this.downloads = [];
        this.isLoading = false;
        this.rejectionMessage = null;
        // Sentinel-2 scenes can't be downloaded unless they've been added to a project --
        // see https://github.com/raster-foundry/raster-foundry/issues/3989
        let datasourceIngestCheck = this.resolve.scene.datasource.name !== 'Sentinel-2' ||
            this.resolve.scene.sceneType === 'COG' && this.resolve.scene.ingestLocation;
        if (this.resolve.scene && datasourceIngestCheck) {
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
        } else if (this.resolve.scene.datasource.name === 'Sentinel-2') {
            this.rejectionMessage =
                'This scene can only be downloaded after it is transformed into a ' +
                'Cloud-Optimized GeoTIFF (COG). ' +
                'To transform this scene into a COG, add it to a project ' +
                '(you can always remove it later).';
        }
    }

    onCopyClick(e, url, type) {
        if (url && url.length) {
            this.copyType = type;
            this.$timeout(() => {
                delete this.copyType;
            }, 1000);
        }
    }
}

const SceneDownloadModalModule = angular.module('components.scenes.sceneDownloadModal', []);

SceneDownloadModalModule.component('rfSceneDownloadModal', SceneDownloadModalComponent);
SceneDownloadModalModule.controller('SceneDownloadModalController', SceneDownloadModalController);

export default SceneDownloadModalModule;
