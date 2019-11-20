import tpl from './index.html';
import _ from 'lodash';

class ProjectSceneItemController {
    constructor($rootScope, sceneService, RasterFoundryRepository, authService, modalService) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
        this.repository = { service: RasterFoundryRepository };
    }

    $onInit() {
        this.datasource = this.scene.datasource;
        this.updateThumbnails();
    }

    getReferenceDate() {
        if (!this.scene) {
            return '';
        }
        let acqDate = this.scene.filterFields.acquisitionDate;
        return acqDate ? acqDate : this.scene.createdAt;
    }

    updateThumbnails() {
        if (this.scene.sceneType === 'COG') {
            let redBand = _.get(
                this.datasource.bands.find(x => {
                    return x.name.toLowerCase() === 'red';
                }),
                'number'
            );
            let greenBand = _.get(
                this.datasource.bands.find(x => {
                    return x.name.toLowerCase() === 'green';
                }),
                'number'
            );
            let blueBand = _.get(
                this.datasource.bands.find(x => {
                    return x.name.toLowerCase() === 'blue';
                }),
                'number'
            );
            let bands = {};
            let atLeastThreeBands = this.datasource.bands.length >= 3;
            if (atLeastThreeBands) {
                bands.red = parseInt(redBand || 0, 10);
                bands.green = parseInt(greenBand || 1, 10);
                bands.blue = parseInt(blueBand || 2, 10);
            } else {
                bands.red = 0;
                bands.green = 0;
                bands.blue = 0;
            }
            this.sceneService
                .cogThumbnail(
                    this.scene.id,
                    this.authService.token(),
                    128,
                    128,
                    bands.red,
                    bands.green,
                    bands.blue
                )
                .then(res => {
                    // Because 504s aren't rejections, apparently
                    if (_.isString(res)) {
                        this.thumbnail = `data:image/png;base64,${res}`;
                    }
                })
                .catch(() => {
                    this.imageError = true;
                });
        } else {
            this.RasterFoundryRepository.getThumbnail(this.scene).then(thumbnail => {
                this.thumbnail = thumbnail;
            });
        }
    }

    openSceneDetailModal(e) {
        e.stopPropagation();
        this.modalService
            .open({
                component: 'rfSceneDetailModal',
                resolve: {
                    scene: () => this.scene,
                    repository: () => this.repository
                }
            })
            .result.catch(() => {});
    }
}

const component = {
    bindings: {
        scene: '<',
        selected: '<',
        onSelect: '&',
        isPreviewable: '<',
        processing: '<'
    },
    templateUrl: tpl,
    controller: ProjectSceneItemController.name,
    transclude: true
};

export default angular
    .module('components.scenes.projectSceneItem', [])
    .controller(ProjectSceneItemController.name, ProjectSceneItemController)
    .component('rfProjectSceneItem', component).name;
