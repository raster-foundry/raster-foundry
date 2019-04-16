/* global BUILDCONFIG */
import tpl from './index.html';
import L from 'leaflet';

let assetLogo = BUILDCONFIG.LOGOFILE
    ? require(`_assets/images/${BUILDCONFIG.LOGOFILE}`)
    : require('_assets/images/raster-foundry-logo.svg');

class ShareProjectController {
    constructor($rootScope, $log, $q, $state, authService, mapService, shareService) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        this.assetLogo = assetLogo;
        this.projectPromise.then((project) => {
            this.project = project;
            this.fitToProject(project);
        }).catch(e => {
            this.$log.error('There was an error fetching the project', e);
            this.$state.go('shareProject.error', {error: e});
        });
    }

    getMap() {
        return this.mapService.getMap('share');
    }

    fitToProject(project) {
        this.getMap().then(map => {
            let bounds = L.geoJSON(project.extent).getBounds();
            map.map.fitBounds(bounds);
        });
    }
}

const component = {
    bindings: {
        projectPromise: '<'
    },
    templateUrl: tpl,
    controller: ShareProjectController.name
};

export default angular
    .module('components.pages.share.project', [])
    .controller(ShareProjectController.name, ShareProjectController)
    .component('rfShareProjectPage', component).name;
