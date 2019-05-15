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
    constructor(sceneService, $timeout, $log, $window) {
        'ngInject';
        this.sceneService = sceneService;
        this.$timeout = $timeout;
        this.$log = $log;
        this.$window = $window;
    }

    $onInit() {
        this.downloads = [];
        this.isLoading = false;
        this.rejectionMessage = null;
        // Sentinel-2 scenes can't be downloaded unless they've been added to a project --
        // see https://github.com/raster-foundry/raster-foundry/issues/3989
        let datasourceIngestCheck =
            this.resolve.scene.datasource.name !== 'Sentinel-2' ||
            (this.resolve.scene.sceneType === 'COG' && this.resolve.scene.ingestLocation);
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
                this.downloads = [
                    {
                        label: scene.name,
                        metadata: scene.metadataFiles || [],
                        images: imageSet
                    }
                ];

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

    onDownloadSentinelMetadata(url) {
        const isXml = url.slice(-3).toLowerCase() === 'xml';
        const fileName = url
            .replace('https://', '')
            .split('/')
            .join('-');
        this.sceneService
            .getSentinelMetadata(this.resolve.scene.id, url)
            .then(resp => {
                let data = [];
                let option = {};
                if (isXml) {
                    const parser = new DOMParser();
                    const xmlDoc = parser.parseFromString(resp.data, 'application/xml');
                    const serializer = new XMLSerializer();
                    data = [serializer.serializeToString(xmlDoc)];
                    option = { type: 'application/xml;charset=utf-8;' };
                } else {
                    data = [JSON.stringify(resp.data)];
                    option = { type: 'application/json;charset=utf-8;' };
                }
                const blob = new Blob(data, option);
                const downloadLink = angular.element('<a></a>');
                downloadLink.attr('href', this.$window.URL.createObjectURL(blob));
                downloadLink.attr('download', fileName);
                downloadLink[0].click();
            })
            .catch(err => {
                this.$log.error(err);
                this.$window.alert('There was an error downloading this metadata file');
            });
    }
}

const SceneDownloadModalModule = angular.module('components.scenes.sceneDownloadModal', []);

SceneDownloadModalModule.component('rfSceneDownloadModal', SceneDownloadModalComponent);
SceneDownloadModalModule.controller('SceneDownloadModalController', SceneDownloadModalController);

export default SceneDownloadModalModule;
